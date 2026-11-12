package com.onebook.server.infrastructure.providers.akniga

import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Durable page snapshots are authoritative; progress and the final JSONL can be rebuilt on resume. */
class AknigaCatalogExporter(
    private val provider: AknigaProvider,
    outputDirectory: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val report: (AknigaExportProgress) -> Unit = {},
) {
    private val root = outputDirectory.toAbsolutePath().normalize()
    private val pages = root.resolve("pages")
    private val json = Json { encodeDefaults = true }
    private val seen = mutableSetOf<String>()
    private val failures = linkedMapOf<String, AknigaExportFailure>()
    private var completedPages = 0
    private var nextPage: String? = AKNIGA_ROOT
    private var startedAt = clock.instant().toString()

    suspend fun export(): AknigaExportProgress {
        Files.createDirectories(pages)
        FileChannel.open(root.resolve("export.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val lock = channel.tryLock() ?: error("Another exporter is already using $root")
            lock.use {
                restore()
                writeProgress("RUNNING")
                try {
                    while (nextPage != null) {
                        currentCoroutineContext().ensureActive()
                        val url = nextPage!!
                        check(completedPages < 20_000) { "Catalog page limit reached; export is incomplete" }
                        val page = provider.catalogPage(url)
                        val books = mutableListOf<AknigaExportBook>()
                        val rejected = mutableListOf<AknigaExportFailure>()
                        for (entry in page.entries) {
                            currentCoroutineContext().ensureActive()
                            if (entry.externalId in seen) continue
                            try {
                                books += AknigaExportBook.from(provider.resolve(entry))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                rejected += AknigaExportFailure(entry.externalId, entry.bookUrl, failure.toString())
                            }
                        }
                        check(books.isNotEmpty() || rejected.isNotEmpty()) { "Catalog repeats already exported books: $url" }
                        val snapshot = AknigaExportPage(completedPages + 1, url, page.nextPageUrl,
                            clock.instant().toString(), books, rejected)
                        publish(pagePath(snapshot.number), json.encodeToString(snapshot))
                        apply(snapshot)
                        writeProgress("RUNNING")
                    }
                    consolidate()
                    return writeProgress(if (failures.isEmpty()) "COMPLETED" else "COMPLETED_WITH_ERRORS")
                } catch (failure: Exception) {
                    writeProgress(if (failure is CancellationException) "CANCELLED" else "FAILED", failure.toString())
                    throw failure
                }
            }
        }
    }

    private fun restore() {
        seen.clear()
        failures.clear()
        completedPages = 0
        nextPage = AKNIGA_ROOT
        val progress = root.resolve("progress.json")
        if (Files.exists(progress)) startedAt = json.decodeFromString<AknigaExportProgress>(Files.readString(progress)).startedAt
        Files.list(pages).use { paths ->
            paths.filter { it.fileName.toString().matches(Regex("[0-9]{6}\\.json")) }.sorted().forEach { path ->
                val page = json.decodeFromString<AknigaExportPage>(Files.readString(path))
                check(path == pagePath(completedPages + 1) && page.number == completedPages + 1 && page.pageUrl == nextPage) {
                    "Broken export checkpoint chain at $path"
                }
                apply(page)
            }
        }
    }

    private fun apply(page: AknigaExportPage) {
        page.failures.forEach { failures[it.externalId] = it }
        page.books.forEach { seen += it.externalId; failures.remove(it.externalId) }
        completedPages = page.number
        nextPage = page.nextPageUrl
    }

    private fun consolidate() {
        val temporary = Files.createTempFile(root, "books-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { writer ->
                val written = mutableSetOf<String>()
                for (number in 1..completedPages) {
                    val page = json.decodeFromString<AknigaExportPage>(Files.readString(pagePath(number)))
                    page.books.forEach { book ->
                        if (written.add(book.externalId)) { writer.write(json.encodeToString(book)); writer.newLine() }
                    }
                }
            }
            move(temporary, root.resolve("books.jsonl"))
        } finally { Files.deleteIfExists(temporary) }
        publish(root.resolve("failures.jsonl"), failures.values.joinToString("\n", postfix = if (failures.isEmpty()) "" else "\n") {
            json.encodeToString(it)
        })
    }

    private fun pagePath(number: Int) = pages.resolve(number.toString().padStart(6, '0') + ".json")

    private fun writeProgress(status: String, error: String? = null): AknigaExportProgress {
        val progress = AknigaExportProgress(status, ProcessHandle.current().pid(), startedAt, clock.instant().toString(),
            completedPages, seen.size, failures.size, nextPage, error)
        publish(root.resolve("progress.json"), json.encodeToString(progress))
        report(progress)
        return progress
    }

    private fun publish(path: Path, content: String) {
        val temporary = Files.createTempFile(path.parent, "export-", ".tmp")
        try {
            Files.writeString(temporary, content)
            move(temporary, path)
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun move(source: Path, target: Path) {
        FileChannel.open(source, StandardOpenOption.WRITE).use { it.force(true) }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

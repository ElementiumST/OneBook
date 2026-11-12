package com.onebook.server.infrastructure

import com.onebook.server.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
private data class CatalogEntry(val id: String, val title: String, val author: String, val file: String)

class FileCatalogRepository(root: Path) : CatalogRepository {
    private val entries = Json.decodeFromString<List<CatalogEntry>>(Files.readString(root.resolve("catalog.json")))
    private val books = entries.map { AudioBook(it.id, it.title, it.author, it.file) }
    init {
        require(books.map { it.id }.distinct().size == books.size) { "ID книг должны быть уникальными" }
        require(books.all { it.id.matches(Regex("[a-zA-Z0-9_-]+")) && it.title.isNotBlank() && it.file.isNotBlank() }) {
            "Некорректная запись каталога"
        }
    }
    override fun books(query: String) = books.filter {
        it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
    }
    override fun book(id: String) = books.find { it.id == id }
}

@Serializable
private data class ListeningState(val library: Set<String> = emptySet(), val positions: Map<String, Long> = emptyMap())

/** Single-process file storage; updates are persisted before publishing the new state. */
class FileListeningRepository(private val path: Path) : ListeningRepository {
    private var state = if (Files.exists(path)) Json.decodeFromString<ListeningState>(Files.readString(path)) else ListeningState()
    @Synchronized override fun library() = state.library.toSet()
    @Synchronized override fun position(id: String) = state.positions[id] ?: 0L
    @Synchronized override fun add(id: String) = update(state.copy(library = state.library + id))
    @Synchronized override fun remove(id: String) = update(state.copy(library = state.library - id))
    @Synchronized override fun savePosition(id: String, positionMs: Long) = update(state.copy(positions = state.positions + (id to positionMs)))
    private fun update(next: ListeningState) {
        val absolute = path.toAbsolutePath()
        Files.createDirectories(absolute.parent)
        val temporary = Files.createTempFile(absolute.parent, "listening-", ".tmp")
        try {
            Files.writeString(temporary, Json.encodeToString(next))
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING)
            }
            state = next
        } finally { Files.deleteIfExists(temporary) }
    }
}

class AudioStorage(root: Path) {
    private val root = root.toRealPath()
    fun resolve(relative: String): java.io.File? {
        val candidate = root.resolve(relative).normalize()
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) return null
        val real = candidate.toRealPath()
        return if (real.startsWith(root)) real.toFile() else null
    }
}

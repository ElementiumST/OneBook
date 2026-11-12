package com.onebook.server.akniga

import com.onebook.server.domain.provider.ProviderBook
import com.onebook.server.infrastructure.providers.akniga.AknigaHtmlParser
import com.onebook.server.infrastructure.providers.akniga.AknigaProvider
import com.onebook.server.infrastructure.providers.akniga.createAknigaHttpClient
import io.ktor.client.engine.config
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration

/** A separate test harness: defaults to fixtures and never writes to the application catalog. */
fun main(args: Array<String>) = runBlocking {
    require(args.size % 2 == 0) { "Expected option/value pairs" }
    val options = args.toList().chunked(2).associate { it[0] to it[1] }
    require(options.keys.all { it in setOf("--aknigaUrl", "--aknigaFile", "--aknigaLimit", "--aknigaMode") }) { "Unknown lab option" }
    val url = options["--aknigaUrl"]
    val file = options["--aknigaFile"]
    val mode = options["--aknigaMode"] ?: "book"
    require(mode in setOf("book", "catalog")) { "aknigaMode must be book or catalog" }
    require(mode == "book" || file != null) { "Catalog file mode requires aknigaFile" }
    val limit = options["--aknigaLimit"]?.let { value ->
        value.toIntOrNull()?.takeIf { it in 1..100 } ?: error("aknigaLimit must be between 1 and 100")
    }
    require(limit == null || (url == null && file == null)) { "Catalog limit cannot be combined with a book URL or file" }
    when {
        file != null -> {
            val html = Files.readString(Path.of(file))
            if (mode == "book") {
                printBook(AknigaHtmlParser().parseBook(html, url ?: SERIES_URL))
            } else {
                val page = AknigaHtmlParser().parseCatalog(html, url ?: "https://akniga.org/")
                page.entries.forEach { entry ->
                    val book = entry.bookOrNull()
                    if (book != null) printBook(book) else println(outputJson.encodeToString(buildJsonObject {
                        put("externalId", entry.externalId)
                        put("bookUrl", entry.bookUrl)
                        put("requiresDetails", true)
                    }))
                }
                System.err.println("Next page: ${page.nextPageUrl ?: "end of catalog"}")
            }
        }
        url != null -> createAknigaHttpClient().use { client ->
            printBook(AknigaProvider(client).book(url))
        }
        limit != null -> {
            System.err.println("Live preview: $limit records; this is not a complete catalog import.")
            createAknigaHttpClient().use { client ->
                AknigaProvider(client).books().take(limit).collect(::printBook)
            }
        }
        else -> {
            System.err.println("Offline fixture preview; no network requests or database writes.")
            createAknigaHttpClient(MockEngine.config {
                addHandler { request ->
                    respond(fixtureForPath(request.url.encodedPath), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
                }
            }).use { client ->
                AknigaProvider(client, requestInterval = Duration.ZERO).books().collect(::printBook)
            }
        }
    }
}

private val outputJson = Json { prettyPrint = true }

private fun printBook(book: ProviderBook) {
    println(outputJson.encodeToString(buildJsonObject {
        put("providerId", "akniga")
        put("externalId", book.externalId)
        put("bookUrl", book.bookUrl)
        put("title", book.metadata.title)
        put("author", book.metadata.author)
        put("narrator", book.metadata.narrator)
        put("coverUrl", book.metadata.coverUrl)
        put("series", book.metadata.series)
        put("durationSeconds", book.metadata.durationSeconds)
    }))
}

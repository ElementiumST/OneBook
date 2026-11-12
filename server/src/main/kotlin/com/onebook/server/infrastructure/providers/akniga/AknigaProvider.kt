package com.onebook.server.infrastructure.providers.akniga

import com.onebook.server.domain.provider.BookProvider
import com.onebook.server.domain.provider.ProviderBook
import com.onebook.server.domain.provider.ProviderDescriptor
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AknigaProvider(
    private val client: HttpClient,
    private val parser: AknigaHtmlParser = AknigaHtmlParser(),
    private val requestInterval: Duration = 1.seconds,
    private val maxPages: Int = 20_000,
    priority: Int = 100,
) : BookProvider {
    override val descriptor = ProviderDescriptor("akniga", priority)

    init {
        require(requestInterval >= Duration.ZERO && requestInterval.isFinite()) { "Invalid request interval" }
        require(maxPages > 0) { "Page limit must be positive" }
    }

    override fun books(): Flow<ProviderBook> = flow {
        val visitedPages = mutableSetOf<String>()
        val emittedIds = mutableSetOf<String>()
        var pageUrl: String? = AKNIGA_ROOT
        while (pageUrl != null) {
            currentCoroutineContext().ensureActive()
            val url = pageUrl
            if (!visitedPages.add(url)) throw AknigaParseException("Catalog pagination loop: $url")
            if (visitedPages.size > maxPages) throw AknigaParseException("Catalog exceeds $maxPages pages; import is incomplete")
            val page = catalogPage(url)
            var addedOnPage = 0
            for (entry in page.entries) {
                if (!emittedIds.add(entry.externalId)) continue
                val book = resolve(entry)
                emit(book)
                addedOnPage++
            }
            if (addedOnPage == 0) throw AknigaParseException("Catalog page contains no new books: $url")
            pageUrl = page.nextPageUrl
        }
    }

    suspend fun book(url: String): ProviderBook {
        val target = portalUrl(url)
        return parser.parseBook(fetch(target), target)
    }

    suspend fun catalogPage(url: String): AknigaCatalogPage {
        val target = portalUrl(url)
        catalogPageNumber(target)
        return parser.parseCatalog(fetch(target), target)
    }

    suspend fun resolve(entry: AknigaCatalogEntry): ProviderBook {
        val book = entry.bookOrNull() ?: book(entry.bookUrl)
        if (book.externalId != entry.externalId) {
            throw AknigaParseException("Book ID changed between catalog and detail page: ${entry.bookUrl}")
        }
        return book
    }

    private suspend fun fetch(url: String): String {
        delay(requestInterval)
        val response = client.get(portalUrl(url)) {
            header(HttpHeaders.UserAgent, "OneBook/0.1 (Akniga metadata parser)")
            header(HttpHeaders.Accept, "text/html, application/xhtml+xml")
        }
        if (!response.status.isSuccess()) throw IOException("Akniga HTTP ${response.status.value}: $url")
        val type = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
        if (type !in listOf("text/html", "application/xhtml+xml")) {
            throw AknigaParseException("Expected HTML from $url, received $type")
        }
        return response.bodyAsText()
    }
}

package com.onebook.server.akniga

import com.onebook.server.application.CatalogSynchronizer
import com.onebook.server.application.ProviderSyncResult
import com.onebook.server.domain.provider.CatalogImportRepository
import com.onebook.server.domain.provider.CatalogImportSession
import com.onebook.server.domain.provider.ProviderBook
import com.onebook.server.domain.provider.ProviderDescriptor
import com.onebook.server.infrastructure.providers.akniga.AknigaParseException
import com.onebook.server.infrastructure.providers.akniga.AknigaProvider
import com.onebook.server.infrastructure.providers.akniga.createAknigaHttpClient
import com.onebook.server.infrastructure.providers.akniga.retryAfterMillis
import io.ktor.client.HttpClient
import io.ktor.client.engine.config
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AknigaProviderTest {
    @Test fun `full catalog is sequential and only incomplete cards cause detail requests`() = runTest {
        val requests = mutableListOf<String>()
        mockClient { request ->
            requests += request.url.encodedPath
            assertEquals("akniga.org", request.url.host)
            assertTrue(request.headers[HttpHeaders.UserAgent]!!.contains("OneBook"))
            html(fixtureForPath(request.url.encodedPath))
        }.use { client ->
            val books = AknigaProvider(client, requestInterval = Duration.ZERO).books().toList()
            assertEquals(listOf("113752", "113645", "113470"), books.map { it.externalId })
            assertEquals("Стругацкий Аркадий, Стругацкий Борис", books[1].metadata.author)
        }
        assertEquals(listOf("/", AUTHORS_URL.removePrefix("https://akniga.org"), "/index/page2/"), requests)
    }

    @Test fun `duplicate boundary books are emitted once`() = runTest {
        val firstCard = fixture("catalog-page1.html").substringAfter("<aside>").substringAfter("</aside>")
            .substringBefore("<div class=\"content__main__articles--item content__main__articles--pageBooks\" data-id=\"127222\"")
        mockClient { request ->
            val page = fixtureForPath(request.url.encodedPath)
            html(if (request.url.encodedPath == "/index/page2/") page.replace("<body>", "<body>$firstCard") else page)
        }.use { client ->
            assertEquals(3, AknigaProvider(client, requestInterval = Duration.ZERO).books().toList().size)
        }
    }

    @Test fun `replayed page with no new ids is rejected even at a different url`() = runTest {
        mockClient { request ->
            html(if (request.url.encodedPath == "/index/page2/") {
                fixture("catalog-page1.html").replace("page2", "page3")
            } else fixtureForPath(request.url.encodedPath))
        }.use { client ->
            assertFailsWith<AknigaParseException> { AknigaProvider(client, requestInterval = Duration.ZERO).books().toList() }
        }
    }

    @Test fun `configured page limit fails rather than reporting a partial catalog as complete`() = runTest {
        mockClient { request -> html(fixtureForPath(request.url.encodedPath)) }.use { client ->
            assertFailsWith<AknigaParseException> {
                AknigaProvider(client, requestInterval = Duration.ZERO, maxPages = 1).books().toList()
            }
        }
    }

    @Test fun `temporary server errors and rate limits are retried`() = runTest {
        var attempts = 0
        mockClient {
            attempts++
            when (attempts) {
                1 -> respond("Unavailable", HttpStatusCode.ServiceUnavailable)
                2 -> respond("Wait", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "1"))
                else -> html(fixture("book-series.html"))
            }
        }.use { client ->
            assertEquals("113752", AknigaProvider(client, requestInterval = Duration.ZERO).book(SERIES_URL).externalId)
        }
        assertEquals(3, attempts)
    }

    @Test fun `permanent errors are not retried and server retries are bounded`() = runTest {
        var attempts = 0
        mockClient { attempts++; respond("Not found", HttpStatusCode.NotFound) }.use { client ->
            assertFailsWith<IOException> { AknigaProvider(client, requestInterval = Duration.ZERO).book(SERIES_URL) }
        }
        assertEquals(1, attempts)
        attempts = 0
        mockClient { attempts++; respond("Unavailable", HttpStatusCode.ServiceUnavailable) }.use { client ->
            assertFailsWith<IOException> { AknigaProvider(client, requestInterval = Duration.ZERO).book(SERIES_URL) }
        }
        assertEquals(3, attempts)
    }

    @Test fun `redirects foreign origins and non html responses are not silently followed`() = runTest {
        var attempts = 0
        mockClient {
            attempts++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.example/"))
        }.use { client ->
            assertFailsWith<IOException> { AknigaProvider(client, requestInterval = Duration.ZERO).book(SERIES_URL) }
            assertFailsWith<AknigaParseException> {
                AknigaProvider(client, requestInterval = Duration.ZERO).book("https://other.example/")
            }
        }
        assertEquals(1, attempts)
        mockClient { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }.use { client ->
            assertFailsWith<AknigaParseException> { AknigaProvider(client, requestInterval = Duration.ZERO).book(SERIES_URL) }
        }
    }

    @Test fun `detail page must match the catalog external id`() = runTest {
        mockClient { request ->
            val responseHtml = fixtureForPath(request.url.encodedPath)
            html(if (request.url.encodedPath == AUTHORS_URL.removePrefix("https://akniga.org")) {
                responseHtml.replace("data-bid=\"113645\"", "data-bid=\"999\"")
            } else responseHtml)
        }.use { client ->
            assertFailsWith<AknigaParseException> { AknigaProvider(client, requestInterval = Duration.ZERO).books().toList() }
        }
    }

    @Test fun `network or layout failure leaves the previous import unpublished`() = runTest {
        val repository = ImportRecorder()
        mockClient { request ->
            html(if (request.url.encodedPath == "/index/page2/") fixture("challenge.html") else fixtureForPath(request.url.encodedPath))
        }.use { client ->
            val synchronizer = CatalogSynchronizer(
                listOf(AknigaProvider(client, requestInterval = Duration.ZERO)), repository,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            assertIs<ProviderSyncResult.Failure>(synchronizer.synchronize().single())
        }
        assertEquals(2, repository.staged)
        assertTrue(!repository.committed)
        assertTrue(repository.closed)
    }

    @Test fun `cancellation closes the import and interrupts a pending request`() = runTest {
        val repository = ImportRecorder()
        val requested = CompletableDeferred<Unit>()
        mockClient {
            requested.complete(Unit)
            awaitCancellation()
        }.use { client ->
            val synchronizer = CatalogSynchronizer(
                listOf(AknigaProvider(client, requestInterval = Duration.ZERO)), repository,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            val job = launch { synchronizer.synchronize() }
            requested.await()
            job.cancelAndJoin()
        }
        assertTrue(repository.closed)
        assertTrue(!repository.committed)
    }

    @Test fun `retry after supports seconds and HTTP dates`() {
        val clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC)
        assertEquals(5000L, retryAfterMillis("5", clock))
        assertEquals(5000L, retryAfterMillis("Mon, 02 Nov 2026 00:00:05 GMT", clock))
        assertNull(retryAfterMillis("bad header", clock))
    }

    private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        createAknigaHttpClient(MockEngine.config { addHandler(handler) })

    private fun MockRequestHandleScope.html(value: String): HttpResponseData =
        respond(value, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
}

private class ImportRecorder : CatalogImportRepository {
    var staged = 0
    var committed = false
    var closed = false

    override fun beginImport(provider: ProviderDescriptor): CatalogImportSession = object : CatalogImportSession {
        override fun stage(book: ProviderBook) { staged++ }
        override fun commit(completedAt: Instant) { committed = true }
        override fun close() { closed = true }
    }
}

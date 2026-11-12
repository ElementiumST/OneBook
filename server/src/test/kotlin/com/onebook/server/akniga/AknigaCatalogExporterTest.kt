package com.onebook.server.akniga

import com.onebook.server.infrastructure.providers.akniga.AknigaCatalogExporter
import com.onebook.server.infrastructure.providers.akniga.AknigaExportProgress
import com.onebook.server.infrastructure.providers.akniga.AknigaProvider
import com.onebook.server.infrastructure.providers.akniga.createAknigaHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.config
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration

class AknigaCatalogExporterTest {
    @Test fun `complete export preserves page checkpoints and writes unique JSONL records`() = runTest {
        val root = Files.createTempDirectory("akniga-export-test-")
        try {
            client().use { client ->
                val progress = AknigaCatalogExporter(AknigaProvider(client, requestInterval = Duration.ZERO), root).export()
                assertEquals("COMPLETED", progress.status)
                assertEquals(2, progress.pagesCompleted)
                assertEquals(3, progress.booksExported)
                assertEquals(0, progress.failedBooks)
            }
            assertEquals(3, Files.readAllLines(root.resolve("books.jsonl")).size)
            assertTrue(Files.exists(root.resolve("pages/000001.json")))
            assertTrue(Files.exists(root.resolve("pages/000002.json")))
            assertEquals("", Files.readString(root.resolve("failures.jsonl")))
        } finally { cleanup(root) }
    }

    @Test fun `failed catalog page resumes from durable checkpoint without refetching earlier pages`() = runTest {
        val root = Files.createTempDirectory("akniga-export-test-")
        try {
            client { path -> if (path == "/index/page2/") fixture("challenge.html") else fixtureForPath(path) }.use { client ->
                assertFailsWith<IllegalArgumentException> {
                    AknigaCatalogExporter(AknigaProvider(client, requestInterval = Duration.ZERO), root).export()
                }
            }
            val failed = Json.decodeFromString<AknigaExportProgress>(Files.readString(root.resolve("progress.json")))
            assertEquals("FAILED", failed.status)
            assertEquals(1, failed.pagesCompleted)
            assertEquals(2, failed.booksExported)
            val requests = mutableListOf<String>()
            client { path -> requests += path; fixtureForPath(path) }.use { client ->
                val resumed = AknigaCatalogExporter(AknigaProvider(client, requestInterval = Duration.ZERO), root).export()
                assertEquals("COMPLETED", resumed.status)
                assertEquals(3, resumed.booksExported)
            }
            assertEquals(listOf("/index/page2/"), requests)
            assertEquals(3, Files.readAllLines(root.resolve("books.jsonl")).size)
        } finally { cleanup(root) }
    }

    @Test fun `individual rejected books remain explicit failures and do not stop later pages`() = runTest {
        val root = Files.createTempDirectory("akniga-export-test-")
        try {
            client { path ->
                if (path == AUTHORS_URL.removePrefix("https://akniga.org")) fixture("challenge.html") else fixtureForPath(path)
            }.use { client ->
                val progress = AknigaCatalogExporter(AknigaProvider(client, requestInterval = Duration.ZERO), root).export()
                assertEquals("COMPLETED_WITH_ERRORS", progress.status)
                assertEquals(2, progress.booksExported)
                assertEquals(1, progress.failedBooks)
                assertEquals(2, progress.pagesCompleted)
            }
            assertTrue(Files.readString(root.resolve("failures.jsonl")).contains("113645"))
        } finally { cleanup(root) }
    }

    private fun client(page: (String) -> String = ::fixtureForPath): HttpClient = createAknigaHttpClient(MockEngine.config {
        addHandler { request ->
            respond(page(request.url.encodedPath), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
        }
    })

    private fun cleanup(path: Path) {
        check(path.toRealPath().startsWith(Path.of(System.getProperty("java.io.tmpdir")).toRealPath()))
        path.toFile().deleteRecursively()
    }
}

package com.onebook.server

import com.onebook.server.infrastructure.AudioStorage
import com.onebook.server.infrastructure.FileListeningRepository
import com.onebook.shared.api.BookDto
import com.onebook.shared.api.ErrorDto
import com.onebook.shared.api.OneBookApi
import com.onebook.shared.api.PositionDto
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.serialization.kotlinx.json.json
import java.nio.file.Files
import kotlin.test.*

class ServerTest {
    @Test fun `shared requests and models work against the server`() {
        val root = Files.createTempDirectory("onebook-contract-test")
        try {
            Files.writeString(root.resolve("catalog.json"), """[{"id":"test","title":"Книга","author":"Автор","file":"test.mp3"}]""")
            testApplication {
                application { oneBookModule(root) }
                val http = createClient { install(ContentNegotiation) { json() } }
                val api = OneBookApi(http, "http://localhost/")
                val book = BookDto("test", "Книга", "Автор")
                assertEquals("OK", api.health())
                assertEquals(listOf(book), api.books("кни"))
                assertTrue(api.books("missing").isEmpty())
                assertEquals(book, api.book("test"))
                assertTrue(api.library().isEmpty())
                api.addToLibrary("test")
                assertEquals(listOf(book), api.library())
                assertEquals(PositionDto(0), api.position("test"))
                api.savePosition("test", PositionDto(1234))
                assertEquals(PositionDto(1234), api.position("test"))
                val invalid = assertFailsWith<ClientRequestException> { api.savePosition("test", PositionDto(-1)) }
                assertEquals(HttpStatusCode.BadRequest, invalid.response.status)
                assertEquals(PositionDto(1234), api.position("test"))
                api.removeFromLibrary("test")
                assertTrue(api.library().isEmpty())
                val missing = assertFailsWith<ClientRequestException> { api.book("missing") }
                assertEquals(HttpStatusCode.NotFound, missing.response.status)
                assertEquals("Книга не найдена", missing.response.body<ErrorDto>().message)
                http.close()
            }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun `catalog library persistence progress and byte ranges`() {
        val root = Files.createTempDirectory("onebook-test")
        try {
            Files.writeString(root.resolve("catalog.json"), """[{"id":"test","title":"Книга","author":"Автор","file":"test.mp3"}]""")
            Files.write(root.resolve("test.mp3"), ByteArray(100) { it.toByte() })
            testApplication {
                application { oneBookModule(root) }
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                assertTrue(client.get("/api/books?q=кни").bodyAsText().contains("Книга"))
                assertEquals("[]", client.get("/api/books?q=missing").bodyAsText())
                assertEquals(HttpStatusCode.NotFound, client.get("/api/books/missing").status)
                assertEquals(HttpStatusCode.NoContent, client.put("/api/library/test").status)
                assertTrue(client.get("/api/library").bodyAsText().contains("Книга"))
                assertEquals(HttpStatusCode.NoContent, client.put("/api/books/test/position") {
                    contentType(ContentType.Application.Json); setBody("""{"positionMs":1234}""")
                }.status)
                assertEquals(HttpStatusCode.BadRequest, client.put("/api/books/test/position") {
                    contentType(ContentType.Application.Json); setBody("""{"positionMs":-1}""")
                }.status)
                val range = client.get("/api/books/test/audio") { header(HttpHeaders.Range, "bytes=10-19") }
                assertEquals(HttpStatusCode.PartialContent, range.status)
                assertEquals("bytes 10-19/100", range.headers[HttpHeaders.ContentRange])
                assertContentEquals(ByteArray(10) { (it + 10).toByte() }, range.readRawBytes())
                assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, client.get("/api/books/test/audio") {
                    header(HttpHeaders.Range, "bytes=200-300")
                }.status)
                assertEquals(HttpStatusCode.OK, client.head("/api/books/test/audio").status)
            }
            val restored = FileListeningRepository(root.resolve("listening.json"))
            assertEquals(setOf("test"), restored.library())
            assertEquals(1234, restored.position("test"))
            assertNull(AudioStorage(root).resolve("../outside.mp3"))
        } finally { root.toFile().deleteRecursively() }
    }
}

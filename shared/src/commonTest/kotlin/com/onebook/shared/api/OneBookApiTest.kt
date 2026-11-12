package com.onebook.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OneBookApiTest {
    @Test fun `book identifiers stay within one URL segment`() = runTest {
        val id = "Автор / книга?#% &"
        val methods = mutableListOf<HttpMethod>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/prefix/api/library/", request.url.encodedPath.substringBeforeLast('/') + "/")
            assertEquals(id, request.url.encodedPath.substringAfterLast('/').decodeURLPart())
            assertTrue(request.url.parameters.isEmpty())
            assertEquals("", request.url.fragment)
            methods += request.method
            respond("", HttpStatusCode.NoContent)
        })
        try {
            val api = OneBookApi(client, "http://localhost/prefix/")
            api.addToLibrary(id)
            api.removeFromLibrary(id)
            assertEquals(listOf(HttpMethod.Put, HttpMethod.Delete), methods)
            val audio = api.audioUrl(id)
            assertEquals(id, audio.substringAfter("/api/books/").substringBeforeLast('/').decodeURLPart())
            assertTrue(audio.endsWith("/audio"))
        } finally { client.close() }
    }

    @Test fun `failed mutation throws even if the provided client accepts errors`() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"message":"Книга не найдена"}""", HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val api = OneBookApi(client, "http://localhost")
            assertFailsWith<ClientRequestException> { api.addToLibrary("missing") }
            assertFailsWith<ClientRequestException> { api.removeFromLibrary("missing") }
        } finally { client.close() }
    }
}

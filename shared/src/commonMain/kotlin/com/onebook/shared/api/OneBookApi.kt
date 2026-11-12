package com.onebook.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * HTTP requests shared by all clients. The caller owns [client] and configures JSON,
 * timeouts and the platform engine. Requests throw on unsuccessful HTTP responses.
 */
class OneBookApi(private val client: HttpClient, baseUrl: String) {
    private val baseUrl = baseUrl.trimEnd('/')

    suspend fun health(): String = client.get("$baseUrl${ApiRoutes.HEALTH}") {
        expectSuccess = true
    }.bodyAsText()

    suspend fun books(query: String = ""): List<BookDto> = client.get("$baseUrl${ApiRoutes.BOOKS}") {
        expectSuccess = true
        parameter(ApiRoutes.QUERY, query)
    }.body()

    suspend fun book(id: String): BookDto = client.get("$baseUrl${ApiRoutes.book(id)}") {
        expectSuccess = true
    }.body()

    suspend fun library(): List<BookDto> = client.get("$baseUrl${ApiRoutes.LIBRARY}") {
        expectSuccess = true
    }.body()

    suspend fun addToLibrary(id: String) {
        client.put("$baseUrl${ApiRoutes.libraryBook(id)}") { expectSuccess = true }
    }

    suspend fun removeFromLibrary(id: String) {
        client.delete("$baseUrl${ApiRoutes.libraryBook(id)}") { expectSuccess = true }
    }

    suspend fun position(id: String): PositionDto = client.get("$baseUrl${ApiRoutes.position(id)}") {
        expectSuccess = true
    }.body()

    suspend fun savePosition(id: String, position: PositionDto) {
        client.put("$baseUrl${ApiRoutes.position(id)}") {
            expectSuccess = true
            contentType(ContentType.Application.Json)
            setBody(position)
        }
    }

    /** URL for a streaming player that manages Range/HEAD requests itself. */
    fun audioUrl(id: String): String = "$baseUrl${ApiRoutes.audio(id)}"
}

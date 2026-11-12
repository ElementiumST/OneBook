package com.onebook.server.api

import com.onebook.server.application.BookService
import com.onebook.server.domain.AudioBook
import com.onebook.server.infrastructure.AudioStorage
import com.onebook.shared.api.ApiRoutes
import com.onebook.shared.api.BookDto
import com.onebook.shared.api.ErrorDto
import com.onebook.shared.api.PositionDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun AudioBook.dto() = BookDto(id, title, author)

fun Route.bookRoutes(service: BookService, audio: AudioStorage) {
    get(ApiRoutes.HEALTH) { call.respondText("OK") }
    get(ApiRoutes.BOOKS) { call.respond(service.books(call.request.queryParameters[ApiRoutes.QUERY].orEmpty()).map { it.dto() }) }
    get(ApiRoutes.BOOK) { call.respond(service.book(call.parameters[ApiRoutes.BOOK_ID]!!).dto()) }
    get(ApiRoutes.AUDIO) {
        val book = service.book(call.parameters[ApiRoutes.BOOK_ID]!!)
        val file = audio.resolve(book.file)
        if (file == null) call.respond(HttpStatusCode.NotFound, ErrorDto("Аудиофайл не найден"))
        else call.respondFile(file)
    }
    get(ApiRoutes.LIBRARY) { call.respond(service.library().map { it.dto() }) }
    put(ApiRoutes.LIBRARY_BOOK) { service.add(call.parameters[ApiRoutes.BOOK_ID]!!); call.respond(HttpStatusCode.NoContent) }
    delete(ApiRoutes.LIBRARY_BOOK) { service.remove(call.parameters[ApiRoutes.BOOK_ID]!!); call.respond(HttpStatusCode.NoContent) }
    get(ApiRoutes.POSITION) { call.respond(PositionDto(service.position(call.parameters[ApiRoutes.BOOK_ID]!!))) }
    put(ApiRoutes.POSITION) {
        service.savePosition(call.parameters[ApiRoutes.BOOK_ID]!!, call.receive<PositionDto>().positionMs)
        call.respond(HttpStatusCode.NoContent)
    }
}

package com.onebook.server

import com.onebook.server.api.*
import com.onebook.server.application.*
import com.onebook.server.infrastructure.*
import com.onebook.shared.api.ErrorDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val root = Path.of(System.getenv("ONEBOOK_DATA_DIR") ?: "data")
    Files.createDirectories(root)
    if (!Files.exists(root.resolve("catalog.json"))) Files.writeString(root.resolve("catalog.json"), "[]")
    embeddedServer(Netty,
        host = System.getenv("ONEBOOK_HOST") ?: "127.0.0.1",
        port = System.getenv("ONEBOOK_PORT")?.toInt() ?: 8080,
    ) { oneBookModule(root) }.start(wait = true)
}

fun Application.oneBookModule(root: Path) {
    install(ContentNegotiation) { json() }
    install(PartialContent)
    install(AutoHeadResponse)
    install(StatusPages) {
        exception<BookNotFound> { call, _ -> call.respond(HttpStatusCode.NotFound, ErrorDto("Книга не найдена")) }
        exception<IllegalArgumentException> { call, cause -> call.respond(HttpStatusCode.BadRequest, ErrorDto(cause.message ?: "Некорректный запрос")) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorDto("Некорректный запрос")) }
        exception<Throwable> { call, cause ->
            this@oneBookModule.log.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("Внутренняя ошибка сервера"))
        }
    }
    val service = BookService(FileCatalogRepository(root), FileListeningRepository(root.resolve("listening.json")))
    val audio = AudioStorage(root)
    routing { bookRoutes(service, audio) }
}

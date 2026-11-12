package com.onebook.shared.api

import io.ktor.http.encodeURLPathPart

object ApiRoutes {
    const val BOOK_ID = "id"
    const val QUERY = "q"
    const val HEALTH = "/health"
    const val BOOKS = "/api/books"
    const val BOOK = "$BOOKS/{$BOOK_ID}"
    const val AUDIO = "$BOOK/audio"
    const val POSITION = "$BOOK/position"
    const val LIBRARY = "/api/library"
    const val LIBRARY_BOOK = "$LIBRARY/{$BOOK_ID}"

    fun book(id: String): String = "$BOOKS/${id.encodeURLPathPart()}"
    fun audio(id: String): String = "${book(id)}/audio"
    fun position(id: String): String = "${book(id)}/position"
    fun libraryBook(id: String): String = "$LIBRARY/${id.encodeURLPathPart()}"
}

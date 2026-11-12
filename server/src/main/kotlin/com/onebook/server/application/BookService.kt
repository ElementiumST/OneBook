package com.onebook.server.application

import com.onebook.server.domain.CatalogRepository
import com.onebook.server.domain.ListeningRepository

class BookNotFound : RuntimeException("Книга не найдена")

class BookService(private val catalog: CatalogRepository, private val listening: ListeningRepository) {
    fun books(query: String) = catalog.books(query.trim())
    fun book(id: String) = catalog.book(id) ?: throw BookNotFound()
    fun library() = listening.library().mapNotNull(catalog::book)
    fun add(id: String) { book(id); listening.add(id) }
    fun remove(id: String) { book(id); listening.remove(id) }
    fun position(id: String): Long { book(id); return listening.position(id) }
    fun savePosition(id: String, positionMs: Long) {
        book(id)
        require(positionMs >= 0) { "Позиция не может быть отрицательной" }
        listening.savePosition(id, positionMs)
    }
}

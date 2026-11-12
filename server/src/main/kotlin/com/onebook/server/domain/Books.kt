package com.onebook.server.domain

data class AudioBook(val id: String, val title: String, val author: String, val file: String)

interface CatalogRepository {
    fun books(query: String = ""): List<AudioBook>
    fun book(id: String): AudioBook?
}

interface ListeningRepository {
    fun library(): Set<String>
    fun add(id: String)
    fun remove(id: String)
    fun position(id: String): Long
    fun savePosition(id: String, positionMs: Long)
}

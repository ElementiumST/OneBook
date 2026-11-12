package com.onebook.core.domain

interface BookRepository {
    suspend fun books(query: String = ""): List<Book>
    suspend fun playbackSource(id: String): PlaybackSource
}

interface LibraryRepository {
    suspend fun books(): List<Book>
    suspend fun add(id: String)
    suspend fun remove(id: String)
}

interface PositionRepository {
    suspend fun get(id: String): Long
    suspend fun save(id: String, positionMs: Long)
}

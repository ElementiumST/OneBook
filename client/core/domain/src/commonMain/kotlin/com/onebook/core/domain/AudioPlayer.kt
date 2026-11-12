package com.onebook.core.domain

import kotlinx.coroutines.flow.StateFlow

/** Application playback session. Implementations own progress restoration and persistence. */
interface AudioPlayer {
    val state: StateFlow<PlayerState>
    suspend fun open(book: Book)
    suspend fun toggle()
    suspend fun seek(positionMs: Long)
    suspend fun pauseAndSave()
    suspend fun savePosition()
    fun close()
}

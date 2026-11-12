package com.onebook.feature.player.presentation

import com.onebook.core.domain.Book
import com.onebook.core.domain.PlayerState
import kotlinx.coroutines.flow.StateFlow

interface PlayerViewModel {
    val state: StateFlow<PlayerState>
    fun open(book: Book)
    fun toggle()
    fun seek(positionMs: Long)
    fun pauseAndSave()
    suspend fun finishPlayback()
}

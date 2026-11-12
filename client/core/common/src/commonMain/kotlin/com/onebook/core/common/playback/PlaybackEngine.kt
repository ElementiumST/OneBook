package com.onebook.core.common.playback

import com.onebook.core.domain.PlaybackSource
import kotlinx.coroutines.flow.StateFlow

data class EngineState(
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val failed: Boolean = false,
)

/** Platform media adapter. It has no knowledge of books, repositories or progress storage. */
interface PlaybackEngine {
    val state: StateFlow<EngineState>
    suspend fun load(source: PlaybackSource, positionMs: Long)
    suspend fun play()
    suspend fun pause()
    suspend fun seek(positionMs: Long)
    fun release()
}

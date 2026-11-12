package com.onebook.mobile.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.onebook.core.common.playback.EngineState
import com.onebook.core.common.playback.PlaybackEngine
import com.onebook.core.domain.PlaybackSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AndroidPlaybackEngine(context: Context) : PlaybackEngine {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override val state = MutableStateFlow(EngineState())

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                state.update { it.copy(playing = false, failed = true) }
            }
        })
        scope.launch { while (isActive) { publish(); delay(500) } }
    }

    private fun publish() {
        state.update { it.copy(
            playing = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
        ) }
    }

    override suspend fun load(source: PlaybackSource, positionMs: Long) = withContext(Dispatchers.Main.immediate) {
        state.value = EngineState(positionMs = positionMs)
        player.setMediaItem(MediaItem.fromUri(source.uri), positionMs)
        player.prepare()
        player.play()
    }
    override suspend fun play() = withContext(Dispatchers.Main.immediate) { player.play(); publish() }
    override suspend fun pause() = withContext(Dispatchers.Main.immediate) { player.pause(); publish() }
    override suspend fun seek(positionMs: Long) = withContext(Dispatchers.Main.immediate) { player.seekTo(positionMs); publish() }
    override fun release() { scope.cancel(); player.release() }
}

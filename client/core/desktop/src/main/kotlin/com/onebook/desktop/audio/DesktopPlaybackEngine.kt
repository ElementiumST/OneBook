package com.onebook.desktop.audio

import com.onebook.core.common.playback.EngineState
import com.onebook.core.common.playback.PlaybackEngine
import com.onebook.core.domain.PlaybackSource
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine

class DesktopPlaybackEngine : PlaybackEngine {
    private var player: MediaPlayer? = null
    private var playWhenReady = false
    override val state = MutableStateFlow(EngineState())

    override suspend fun load(source: PlaybackSource, positionMs: Long) = onFxThread {
        val previous = player
        player = null
        previous?.dispose()
        state.value = EngineState(positionMs = positionMs)
        val media = Media(source.uri)
        val next = MediaPlayer(media)
        player = next
        playWhenReady = true
        fun fail() {
            if (player === next) state.update { it.copy(playing = false, failed = true) }
        }
        media.setOnError { fail() }
        next.setOnError { fail() }
        next.setOnReady {
            if (player === next) {
                state.update { it.copy(durationMs = next.totalDuration.toMillis().toLong().coerceAtLeast(0)) }
                next.seek(Duration.millis(positionMs.toDouble()))
                if (playWhenReady) next.play()
            }
        }
        next.currentTimeProperty().addListener { _, _, value ->
            if (player === next) state.update { it.copy(positionMs = value.toMillis().toLong().coerceAtLeast(0)) }
        }
        next.statusProperty().addListener { _, _, status ->
            if (player === next) state.update { it.copy(playing = status == MediaPlayer.Status.PLAYING) }
        }
        next.setOnEndOfMedia {
            if (player === next) state.update { it.copy(playing = false) }
        }
    }
    override suspend fun play() = onFxThread { playWhenReady = true; player?.play(); Unit }
    override suspend fun pause() = onFxThread {
        playWhenReady = false
        player?.pause()
        state.update { it.copy(playing = false) }
    }
    override suspend fun seek(positionMs: Long) = onFxThread {
        player?.seek(Duration.millis(positionMs.toDouble()))
        state.update { it.copy(positionMs = positionMs) }
    }
    override fun release() = Platform.runLater {
        val previous = player
        player = null
        previous?.dispose()
    }

    /** Await completion so saving progress cannot race a queued seek or pause. */
    private suspend fun onFxThread(action: () -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        Platform.runLater {
            if (continuation.isActive) {
                try { action(); continuation.resume(Unit) }
                catch (e: Exception) { continuation.resumeWithException(e) }
            }
        }
    }
}

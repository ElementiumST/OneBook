package com.onebook.core.common.playback

import com.onebook.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One playback session shared by all screens; the DI container owns its lifetime. */
class CommonAudioPlayer(
    private val engine: PlaybackEngine,
    private val getSource: GetPlaybackSource,
    private val getPosition: GetListeningPosition,
    private val saveListeningPosition: SaveListeningPosition,
    private val scope: CoroutineScope,
) : AudioPlayer {
    override val state = MutableStateFlow(PlayerState())
    private val mutex = Mutex()
    private var closed = false

    init {
        scope.launch { engine.state.collect { publishEngineState() } }
        scope.launch { while (isActive) { delay(5000); savePosition() } }
    }

    override suspend fun open(book: Book) = mutex.withLock {
        check(!closed)
        perform(PlaybackFailure.OpenBook) {
            if (state.value.book?.id == book.id && engine.state.value.durationMs > 0 && !engine.state.value.failed) {
                engine.play()
            } else {
                engine.pause()
                saveCurrent()
                val source = getSource(book)
                val position = getPosition(book)
                engine.load(source, position)
                state.value = PlayerState(book = book)
            }
            state.update { it.copy(failure = null) }
            publishEngineState()
        }
    }

    override suspend fun toggle() = mutex.withLock {
        if (closed || state.value.book == null) return@withLock
        perform(PlaybackFailure.PlayAudio) {
            if (engine.state.value.playing) { engine.pause(); saveCurrent() } else engine.play()
            publishEngineState()
        }
    }

    override suspend fun seek(positionMs: Long) = mutex.withLock {
        if (closed || state.value.book == null || engine.state.value.durationMs <= 0) return@withLock
        perform(PlaybackFailure.PlayAudio) {
            engine.seek(positionMs.coerceIn(0, engine.state.value.durationMs))
            publishEngineState()
            saveCurrent()
        }
    }

    override suspend fun pauseAndSave() = mutex.withLock {
        if (closed) return@withLock
        perform(PlaybackFailure.PlayAudio) { engine.pause(); publishEngineState(); saveCurrent() }
    }

    override suspend fun savePosition() = mutex.withLock { if (!closed) saveCurrent() }

    private suspend fun saveCurrent() {
        val book = state.value.book ?: return
        val playback = engine.state.value
        // Loading or failed media must not overwrite the last restored position.
        if (playback.durationMs <= 0 || playback.failed) return
        perform(PlaybackFailure.SavePosition) {
            saveListeningPosition(book, playback.positionMs)
            state.update { if (it.failure == PlaybackFailure.SavePosition) it.copy(failure = null) else it }
        }
    }

    private suspend fun perform(failure: PlaybackFailure, action: suspend () -> Unit) {
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { state.update { it.copy(failure = failure) } }
    }

    private fun publishEngineState() {
        val playback = engine.state.value
        state.update {
            it.copy(
                playing = playback.playing,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                failure = if (playback.failed) PlaybackFailure.PlayAudio else it.failure,
            )
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        engine.release()
    }
}

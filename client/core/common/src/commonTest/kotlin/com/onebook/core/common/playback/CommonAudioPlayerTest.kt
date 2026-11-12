package com.onebook.core.common.playback

import com.onebook.core.domain.*
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CommonAudioPlayerTest {
    private val first = Book("first", "Первая книга", "Автор")
    private val second = Book("second", "Вторая книга", "Автор")

    @Test fun `opening restores progress and switching saves previous book`() = runTest {
        val engine = FakeEngine()
        val positions = FakePositions().apply { values[first.id] = 12000 }
        val player = create(engine, positions, backgroundScope)
        player.open(first)
        assertEquals(PlaybackSource("audio:first"), engine.loaded)
        assertEquals(12000L, player.state.value.positionMs)
        engine.state.value = engine.state.value.copy(positionMs = 23000)
        player.open(second)
        assertEquals(23000L, positions.values[first.id])
        assertEquals(second, player.state.value.book)
        assertEquals(0L, player.state.value.positionMs)
        player.close()
        player.close()
        assertEquals(1, engine.releases)
    }

    @Test fun `pause seek and autosave persist actual engine position`() = runTest {
        val engine = FakeEngine()
        val positions = FakePositions()
        val player = create(engine, positions, backgroundScope)
        player.open(first)
        player.seek(90000)
        assertEquals(60000L, positions.values[first.id])
        player.seek(-100)
        assertEquals(0L, positions.values[first.id])
        engine.state.value = engine.state.value.copy(positionMs = 17000)
        player.pauseAndSave()
        assertFalse(player.state.value.playing)
        assertEquals(17000L, positions.values[first.id])
        engine.state.value = engine.state.value.copy(positionMs = 24000)
        runCurrent()
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(24000L, positions.values[first.id])
        player.close()
    }

    @Test fun `loading and failed media do not overwrite restored progress`() = runTest {
        val engine = FakeEngine().apply { ready = false }
        val positions = FakePositions().apply { values[first.id] = 19000 }
        val player = create(engine, positions, backgroundScope)
        player.open(first)
        engine.state.value = EngineState(positionMs = 0)
        player.savePosition()
        assertEquals(19000L, positions.values[first.id])
        engine.state.value = EngineState(durationMs = 60000, failed = true)
        player.savePosition()
        assertEquals(19000L, positions.values[first.id])
        runCurrent()
        assertEquals(PlaybackFailure.PlayAudio, player.state.value.failure)
        player.close()
    }

    @Test fun `save failure is visible and a later success clears it`() = runTest {
        val positions = FakePositions()
        val player = create(FakeEngine(), positions, backgroundScope)
        player.open(first)
        positions.failSaving = true
        player.pauseAndSave()
        assertEquals(PlaybackFailure.SavePosition, player.state.value.failure)
        positions.failSaving = false
        player.savePosition()
        assertNull(player.state.value.failure)
        player.close()
    }

    @Test fun `failed opening preserves previous book and cancellation propagates`() = runTest {
        val positions = FakePositions()
        val player = create(FakeEngine(), positions, backgroundScope)
        player.open(first)
        positions.readFailure = IllegalStateException("offline")
        player.open(second)
        assertEquals(first, player.state.value.book)
        assertEquals(PlaybackFailure.OpenBook, player.state.value.failure)
        positions.readFailure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { player.open(second) }
        player.close()
    }

    private fun create(engine: FakeEngine, positions: FakePositions, scope: kotlinx.coroutines.CoroutineScope): CommonAudioPlayer {
        val books = object : BookRepository {
            override suspend fun books(query: String) = listOf(first, second)
            override suspend fun playbackSource(id: String) = PlaybackSource("audio:$id")
        }
        return CommonAudioPlayer(engine, GetPlaybackSource(books), GetListeningPosition(positions), SaveListeningPosition(positions), scope)
    }

    private class FakePositions : PositionRepository {
        val values = mutableMapOf<String, Long>()
        var failSaving = false
        var readFailure: Exception? = null
        override suspend fun get(id: String): Long { readFailure?.let { throw it }; return values[id] ?: 0 }
        override suspend fun save(id: String, positionMs: Long) {
            if (failSaving) error("offline")
            values[id] = positionMs
        }
    }

    private class FakeEngine : PlaybackEngine {
        override val state = MutableStateFlow(EngineState())
        var loaded: PlaybackSource? = null
        var releases = 0
        var ready = true
        override suspend fun load(source: PlaybackSource, positionMs: Long) {
            loaded = source
            state.value = EngineState(playing = ready, positionMs = positionMs, durationMs = if (ready) 60000 else 0)
        }
        override suspend fun play() { state.value = state.value.copy(playing = true) }
        override suspend fun pause() { state.value = state.value.copy(playing = false) }
        override suspend fun seek(positionMs: Long) { state.value = state.value.copy(positionMs = positionMs) }
        override fun release() { releases++ }
    }
}

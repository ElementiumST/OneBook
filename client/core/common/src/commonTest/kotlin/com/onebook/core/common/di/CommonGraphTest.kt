package com.onebook.core.common.di

import com.onebook.core.common.playback.EngineState
import com.onebook.core.common.playback.PlaybackEngine
import com.onebook.core.domain.AudioPlayer
import com.onebook.core.data.cache.RequestCache
import com.onebook.core.domain.PlaybackSource
import com.onebook.feature.catalog.presentation.CatalogViewModel
import com.onebook.feature.library.presentation.LibraryViewModel
import com.onebook.feature.player.presentation.PlayerViewModel
import com.onebook.feature.search.presentation.SearchViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommonGraphTest {
    @Test
    fun resolvesAllFeaturesAndSharesPlaybackSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val playbackEngine = TestPlaybackEngine()
        val httpEngine = MockEngine { respond("[]") }
        val factory = object : HttpClientEngineFactory<HttpClientEngineConfig> {
            override fun create(block: HttpClientEngineConfig.() -> Unit) = httpEngine
        }
        val cache = TestRequestCache()
        val graph = createCommonGraph("http://localhost", factory, { cache }) { playbackEngine }
        try {
            val catalog = graph.koin.get<CatalogViewModel>()
            val search = graph.koin.get<SearchViewModel>()
            val library = graph.koin.get<LibraryViewModel>()
            val player = graph.koin.get<PlayerViewModel>()
            assertFalse(catalog.state.value.loading)
            assertTrue(search.query.value.isEmpty())
            assertTrue(library.state.value.books.isEmpty())
            assertSame(graph.koin.get<AudioPlayer>().state, player.state)
            assertSame(player.state, graph.koin.get<PlayerViewModel>().state)
            assertSame(graph.koin.get<HttpClient>(), graph.koin.get<HttpClient>())
            assertFalse(playbackEngine.released)
        } finally {
            graph.close()
            httpEngine.close()
            Dispatchers.resetMain()
        }
        assertTrue(playbackEngine.released)
        assertTrue(cache.closed)
    }
}

private class TestRequestCache : RequestCache {
    var closed = false
        private set
    override suspend fun read(server: String, key: String): String? = null
    override suspend fun write(server: String, key: String, payload: String) {}
    override suspend fun remove(server: String, key: String) {}
    override fun close() { closed = true }
}

private class TestPlaybackEngine : PlaybackEngine {
    override val state = MutableStateFlow(EngineState())
    var released = false
        private set
    override suspend fun load(source: PlaybackSource, positionMs: Long) {}
    override suspend fun play() {}
    override suspend fun pause() {}
    override suspend fun seek(positionMs: Long) {}
    override fun release() { released = true }
}

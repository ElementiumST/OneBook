package com.onebook.core.common.di

import com.onebook.core.common.playback.PlaybackEngine
import com.onebook.core.data.di.dataModule
import com.onebook.core.data.cache.RequestCache
import com.onebook.feature.catalog.di.catalogModule
import com.onebook.feature.library.di.libraryModule
import com.onebook.feature.player.di.playerModule
import com.onebook.feature.search.di.searchModule
import io.ktor.client.engine.HttpClientEngineFactory
import org.koin.dsl.koinApplication

fun createCommonGraph(
    baseUrl: String,
    httpEngine: HttpClientEngineFactory<*>,
    createCache: () -> RequestCache,
    createPlaybackEngine: () -> PlaybackEngine,
) = koinApplication {
    modules(
        dataModule(baseUrl, httpEngine, createCache),
        commonModule(createPlaybackEngine),
        catalogModule,
        searchModule,
        libraryModule,
        playerModule,
    )
}

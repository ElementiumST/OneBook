package com.onebook.desktop.di

import com.onebook.core.common.di.createCommonGraph
import com.onebook.core.data.cache.createSqliteRequestCache
import com.onebook.desktop.audio.DesktopPlaybackEngine
import io.ktor.client.engine.cio.CIO

fun createGraph(baseUrl: String) = createCommonGraph(
    baseUrl = baseUrl,
    httpEngine = CIO,
    createCache = { createSqliteRequestCache(java.io.File(System.getProperty("user.home"), ".onebook/requests.db").absolutePath) },
    createPlaybackEngine = { DesktopPlaybackEngine() },
)

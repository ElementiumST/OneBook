package com.onebook.mobile.di

import android.content.Context
import com.onebook.core.common.di.createCommonGraph
import com.onebook.core.data.cache.createSqliteRequestCache
import com.onebook.mobile.audio.AndroidPlaybackEngine
import io.ktor.client.engine.okhttp.OkHttp

fun createGraph(context: Context, baseUrl: String) = createCommonGraph(
    baseUrl = baseUrl,
    httpEngine = OkHttp,
    createCache = { createSqliteRequestCache(context.applicationContext) },
    createPlaybackEngine = { AndroidPlaybackEngine(context.applicationContext) },
)

package com.onebook.server.infrastructure.providers.akniga

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import java.io.IOException
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun createAknigaHttpClient(): HttpClient = createAknigaHttpClient(CIO)

/** The client owns its engine; injecting an engine factory keeps all network tests offline. */
fun <T : HttpClientEngineConfig> createAknigaHttpClient(engine: HttpClientEngineFactory<T>): HttpClient = HttpClient(engine) {
    expectSuccess = false
    followRedirects = false
    install(HttpRequestRetry) {
        maxRetries = 2
        retryIf { _, response -> response.status.value == 429 || response.status.value in 500..599 }
        retryOnExceptionIf { _, cause -> cause is IOException }
        delayMillis { attempt ->
            retryAfterMillis(response?.headers?.get("Retry-After")) ?: (1000L shl (attempt - 1).coerceIn(0, 5))
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }
}

internal fun retryAfterMillis(value: String?, clock: Clock = Clock.systemUTC()): Long? {
    if (value == null) return null
    value.trim().toLongOrNull()?.let { seconds ->
        return seconds.takeIf { it >= 0 }?.let { (it.coerceAtMost(Long.MAX_VALUE / 1000) * 1000).coerceAtLeast(1000) }
    }
    return runCatching {
        val instant = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        java.time.Duration.between(clock.instant(), instant).toMillis().coerceAtLeast(1000)
    }.getOrNull()
}

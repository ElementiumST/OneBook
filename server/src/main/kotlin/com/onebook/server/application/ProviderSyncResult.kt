package com.onebook.server.application

import java.time.Instant

sealed class ProviderSyncResult {
    abstract val providerId: String

    data class Success(
        override val providerId: String,
        val importedSources: Long,
        val completedAt: Instant,
    ) : ProviderSyncResult()

    data class Failure(
        override val providerId: String,
        val cause: Exception,
    ) : ProviderSyncResult()
}

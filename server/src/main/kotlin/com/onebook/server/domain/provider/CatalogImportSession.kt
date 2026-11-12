package com.onebook.server.domain.provider

import java.time.Instant

interface CatalogImportSession : AutoCloseable {
    /** Stage one source. Reject repeated external IDs within the same import. */
    fun stage(book: ProviderBook)

    /**
     * Atomically upsert books and sources, match BookMetadata.identity(), preserve internal IDs,
     * and mark missing sources of this provider unavailable. Other providers remain untouched.
     * Update lastSeenAt for staged sources and resolve preferred metadata by source priority.
     * A successful empty snapshot marks all sources of this provider unavailable.
     */
    fun commit(completedAt: Instant)

    /** Discard uncommitted staging data, including after failures and coroutine cancellation. */
    override fun close()
}

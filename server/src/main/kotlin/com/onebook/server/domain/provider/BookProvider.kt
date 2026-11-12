package com.onebook.server.domain.provider

import kotlinx.coroutines.flow.Flow

/** One implementation per portal, responsible for HTTP, pagination and parsing. */
interface BookProvider {
    val descriptor: ProviderDescriptor

    /**
     * Emit the entire catalog, with each external ID occurring once.
     * Complete normally only after the last page has been fetched and parsed successfully.
     * Throw on incomplete responses, exhausted retries or invalid required metadata.
     * Never turn failures into an empty or partially successful catalog.
     */
    fun books(): Flow<ProviderBook>
}

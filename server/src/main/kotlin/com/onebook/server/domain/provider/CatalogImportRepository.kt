package com.onebook.server.domain.provider

interface CatalogImportRepository {
    /** Open an isolated staging area. Readers must continue to see the previous committed data. */
    fun beginImport(provider: ProviderDescriptor): CatalogImportSession
}

package com.onebook.server.domain.provider

import java.util.Locale

/** A smaller priority value selects this provider ahead of other available sources. */
data class ProviderDescriptor(val id: String, val priority: Int = 100) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9_-]*"))) { "Provider ID must be a stable lowercase identifier" }
    }
}

data class BookMetadata(
    val title: String,
    val author: String,
    val narrator: String,
    val coverUrl: String? = null,
    val series: String? = null,
    val durationSeconds: Long? = null,
) {
    init {
        require(title.isNotBlank() && author.isNotBlank() && narrator.isNotBlank()) {
            "Title, author and narrator are required for deduplication"
        }
        require(durationSeconds == null || durationSeconds >= 0) { "Duration cannot be negative" }
    }

    fun identity() = BookIdentity(normalize(title), normalize(author), normalize(narrator))
}

/** Compare normalized fields together, never by concatenating them into an ambiguous string. */
data class BookIdentity(val title: String, val author: String, val narrator: String)

/** bookUrl identifies the book at the portal; it does not imply a playable audio URL. */
data class ProviderBook(val externalId: String, val bookUrl: String, val metadata: BookMetadata) {
    init {
        require(externalId.isNotBlank()) { "External book ID is required" }
        require(bookUrl.isNotBlank()) { "Book URL is required" }
    }
}

private val whitespace = Regex("[\\s\\p{Z}]+")

private fun normalize(value: String): String = value.replace(whitespace, " ").trim().lowercase(Locale.ROOT)

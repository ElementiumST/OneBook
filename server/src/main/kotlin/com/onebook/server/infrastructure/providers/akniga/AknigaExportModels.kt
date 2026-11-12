package com.onebook.server.infrastructure.providers.akniga

import com.onebook.server.domain.provider.ProviderBook
import kotlinx.serialization.Serializable

@Serializable
data class AknigaExportBook(
    val externalId: String,
    val bookUrl: String,
    val title: String,
    val author: String,
    val narrator: String,
    val coverUrl: String?,
    val series: String?,
    val durationSeconds: Long?,
    val providerId: String = "akniga",
) {
    companion object {
        fun from(book: ProviderBook) = AknigaExportBook(
            book.externalId, book.bookUrl, book.metadata.title, book.metadata.author, book.metadata.narrator,
            book.metadata.coverUrl, book.metadata.series, book.metadata.durationSeconds,
        )
    }
}

@Serializable
data class AknigaExportFailure(val externalId: String, val bookUrl: String, val error: String)

@Serializable
data class AknigaExportPage(
    val number: Int,
    val pageUrl: String,
    val nextPageUrl: String?,
    val completedAt: String,
    val books: List<AknigaExportBook>,
    val failures: List<AknigaExportFailure>,
)

@Serializable
data class AknigaExportProgress(
    val status: String,
    val pid: Long,
    val startedAt: String,
    val updatedAt: String,
    val pagesCompleted: Int,
    val booksExported: Int,
    val failedBooks: Int,
    val nextPageUrl: String?,
    val error: String? = null,
)

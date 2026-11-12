package com.onebook.server.application

import com.onebook.server.domain.provider.BookProvider
import com.onebook.server.domain.provider.CatalogImportRepository
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coordinates imports; portal details and database transactions belong behind domain contracts. */
class CatalogSynchronizer(
    providers: List<BookProvider>,
    private val repository: CatalogImportRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val providers = providers.toList()
    private val mutex = Mutex()

    init {
        require(this.providers.map { it.descriptor.id }.distinct().size == this.providers.size) {
            "Provider IDs must be unique"
        }
    }

    /** One singleton instance serializes startup, scheduled and future manual synchronizations. */
    suspend fun synchronize(): List<ProviderSyncResult> = mutex.withLock {
        withContext(ioDispatcher) {
            providers.map { provider ->
                currentCoroutineContext().ensureActive()
                try {
                    repository.beginImport(provider.descriptor).use { session ->
                        var importedSources = 0L
                        provider.books().collect { book ->
                            currentCoroutineContext().ensureActive()
                            session.stage(book)
                            importedSources++
                        }
                        currentCoroutineContext().ensureActive()
                        val completedAt = clock.instant()
                        session.commit(completedAt)
                        ProviderSyncResult.Success(provider.descriptor.id, importedSources, completedAt)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    ProviderSyncResult.Failure(provider.descriptor.id, failure)
                }
            }
        }
    }
}

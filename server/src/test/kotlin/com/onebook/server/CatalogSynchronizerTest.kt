package com.onebook.server

import com.onebook.server.application.CatalogSynchronizer
import com.onebook.server.application.ProviderSyncResult
import com.onebook.server.domain.provider.BookMetadata
import com.onebook.server.domain.provider.BookProvider
import com.onebook.server.domain.provider.CatalogImportRepository
import com.onebook.server.domain.provider.CatalogImportSession
import com.onebook.server.domain.provider.ProviderBook
import com.onebook.server.domain.provider.ProviderDescriptor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogSynchronizerTest {
    private val book = ProviderBook("external-1", "https://portal.example/books/1", BookMetadata("Книга", "Автор", "Чтец"))
    private val completedAt = Instant.parse("2026-11-02T00:00:00Z")
    private val clock = Clock.fixed(completedAt, ZoneOffset.UTC)

    @Test fun `identity ignores case and whitespace but includes author and narrator`() {
        val identity = book.metadata.identity()
        assertEquals(identity, BookMetadata("  КНИГА\u00a0", "автор", "чтец").identity())
        assertEquals(
            BookMetadata("Две  части", "Имя Автора", "Имя Чтеца").identity(),
            BookMetadata("две\tчасти", "имя\nавтора", "имя чтеца").identity(),
        )
        assertNotEquals(identity, book.metadata.copy(author = "Другой автор").identity())
        assertNotEquals(identity, book.metadata.copy(narrator = "Другой чтец").identity())
        assertNotEquals(identity, book.metadata.copy(title = "Книга!").identity())
    }

    @Test fun `successful provider commits once after the entire flow completes`() = runTest {
        val repository = RecordingRepository()
        val provider = provider("first", flow {
            emit(book)
            assertTrue(repository.committed.isEmpty())
            emit(book.copy(externalId = "external-2"))
        })
        val synchronizer = CatalogSynchronizer(listOf(provider), repository, clock, StandardTestDispatcher(testScheduler))

        val result = assertIs<ProviderSyncResult.Success>(synchronizer.synchronize().single())

        assertEquals(2L, result.importedSources)
        assertEquals(completedAt, result.completedAt)
        assertEquals(listOf(book, book.copy(externalId = "external-2")), repository.committed["first"])
        assertTrue(repository.sessions.single().closed)
        assertEquals(completedAt, repository.sessions.single().committedAt)
    }

    @Test fun `partial failure preserves previous snapshot and does not stop other providers`() = runTest {
        val repository = RecordingRepository()
        val previous = book.copy(externalId = "old")
        repository.committed["broken"] = listOf(previous)
        val failure = IllegalStateException("Second page failed")
        val synchronizer = CatalogSynchronizer(
            listOf(provider("broken", flow { emit(book); throw failure }), provider("healthy", flowOf(book))),
            repository, clock, StandardTestDispatcher(testScheduler),
        )

        val results = synchronizer.synchronize()

        assertEquals(failure, assertIs<ProviderSyncResult.Failure>(results[0]).cause)
        assertIs<ProviderSyncResult.Success>(results[1])
        assertEquals(listOf(previous), repository.committed["broken"])
        assertEquals(listOf(book), repository.committed["healthy"])
        assertTrue(repository.sessions.all { it.closed })
    }

    @Test fun `successful empty catalog is committed`() = runTest {
        val repository = RecordingRepository()
        repository.committed["empty"] = listOf(book)
        val synchronizer = CatalogSynchronizer(
            listOf(provider("empty", flowOf())), repository, clock, StandardTestDispatcher(testScheduler),
        )

        assertEquals(0L, assertIs<ProviderSyncResult.Success>(synchronizer.synchronize().single()).importedSources)
        assertEquals(emptyList(), repository.committed["empty"])
    }

    @Test fun `cancellation discards staged data and does not start the next provider`() = runTest {
        val repository = RecordingRepository()
        val synchronizer = CatalogSynchronizer(
            listOf(provider("first", flow { emit(book); awaitCancellation() }), provider("next", flowOf(book))),
            repository, clock, StandardTestDispatcher(testScheduler),
        )
        val job = launch { synchronizer.synchronize() }
        runCurrent()
        assertEquals(1, repository.sessions.size)

        job.cancelAndJoin()

        assertTrue(repository.committed.isEmpty())
        assertTrue(repository.sessions.single().closed)
    }

    @Test fun `repeated external ids fail the import without publishing staged data`() = runTest {
        val repository = RecordingRepository()
        val synchronizer = CatalogSynchronizer(
            listOf(provider("first", flowOf(book, book))), repository, clock, StandardTestDispatcher(testScheduler),
        )

        assertIs<ProviderSyncResult.Failure>(synchronizer.synchronize().single())
        assertTrue(repository.committed.isEmpty())
        assertTrue(repository.sessions.single().closed)
    }

    @Test fun `concurrent synchronization calls do not overlap`() = runTest {
        val repository = RecordingRepository()
        val gate = Channel<Unit>(Channel.UNLIMITED)
        var invocations = 0
        val synchronizer = CatalogSynchronizer(
            listOf(provider("first", flow { invocations++; emit(book); gate.receive() })),
            repository, clock, StandardTestDispatcher(testScheduler),
        )
        val first = async { synchronizer.synchronize() }
        runCurrent()
        val second = async { synchronizer.synchronize() }
        runCurrent()
        assertEquals(1, invocations)

        gate.send(Unit)
        runCurrent()
        assertEquals(2, invocations)
        assertTrue(first.isCompleted)
        assertTrue(!second.isCompleted)
        gate.send(Unit)
        second.await()
        gate.close()
        assertEquals(2, repository.sessions.size)
    }

    @Test fun `provider registration requires distinct ids`() {
        val first = provider("same", flowOf(book))
        assertFailsWith<IllegalArgumentException> {
            CatalogSynchronizer(listOf(first, first), RecordingRepository())
        }
    }

    private fun provider(id: String, books: Flow<ProviderBook>) = object : BookProvider {
        override val descriptor = ProviderDescriptor(id)
        override fun books() = books
    }
}

/** Exercises the import lifecycle only; database reconciliation needs separate integration tests. */
private class RecordingRepository : CatalogImportRepository {
    val committed = mutableMapOf<String, List<ProviderBook>>()
    val sessions = mutableListOf<RecordingSession>()

    override fun beginImport(provider: ProviderDescriptor): CatalogImportSession =
        RecordingSession(provider.id, committed).also(sessions::add)
}

private class RecordingSession(
    private val providerId: String,
    private val committed: MutableMap<String, List<ProviderBook>>,
) : CatalogImportSession {
    private val staged = mutableListOf<ProviderBook>()
    var closed = false
    var committedAt: Instant? = null

    override fun stage(book: ProviderBook) {
        check(staged.none { it.externalId == book.externalId }) { "Duplicate external ID" }
        staged += book
    }

    override fun commit(completedAt: Instant) {
        committed[providerId] = staged.toList()
        committedAt = completedAt
    }

    override fun close() {
        staged.clear()
        closed = true
    }
}

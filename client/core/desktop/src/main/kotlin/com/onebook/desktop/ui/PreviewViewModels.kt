package com.onebook.desktop.ui

import com.onebook.core.domain.Book
import com.onebook.core.domain.PlayerState
import com.onebook.core.ui.BooksState
import com.onebook.feature.catalog.presentation.CatalogViewModel
import com.onebook.feature.library.presentation.LibraryViewModel
import com.onebook.feature.player.presentation.PlayerViewModel
import com.onebook.feature.search.presentation.SearchViewModel
import kotlinx.coroutines.flow.MutableStateFlow

internal object PreviewViewModels {
    private val book = Book("preview", "Мастер и Маргарита", "Михаил Булгаков")

    val catalog = object : CatalogViewModel {
        override val state = MutableStateFlow(BooksState(books = listOf(book)))
        override fun refresh() {}
    }
    val search = object : SearchViewModel {
        override val state = MutableStateFlow(BooksState(books = listOf(book)))
        override val query = MutableStateFlow("Мастер")
        override fun search(query: String) {}
    }
    val library = object : LibraryViewModel {
        override val state = MutableStateFlow(BooksState(books = listOf(book)))
        override val message = MutableStateFlow<String?>(null)
        override fun refresh() {}
        override fun clearMessage() {}
        override fun add(book: Book) {}
        override fun remove(book: Book) {}
    }
    val player = object : PlayerViewModel {
        override val state = MutableStateFlow(PlayerState(book = book, durationMs = 3600000))
        override fun open(book: Book) {}
        override fun toggle() {}
        override fun seek(positionMs: Long) {}
        override fun pauseAndSave() {}
        override suspend fun finishPlayback() {}
    }
}

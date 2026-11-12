package com.onebook.feature.library.presentation

import com.onebook.core.ui.BooksState
import com.onebook.core.domain.Book
import kotlinx.coroutines.flow.StateFlow

interface LibraryViewModel {
    val state: StateFlow<BooksState>
    val message: StateFlow<String?>
    fun clearMessage()
    fun refresh()
    fun add(book: Book)
    fun remove(book: Book)
}

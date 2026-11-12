package com.onebook.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onebook.core.ui.BooksState
import com.onebook.core.domain.SearchBooks
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal class SearchViewModelImpl(private val searchBooks: SearchBooks) : ViewModel(), SearchViewModel {
    override val query = MutableStateFlow("")
    override val state = MutableStateFlow(BooksState())
    private var request: Job? = null
    override fun search(query: String) {
        this.query.value = query
        request?.cancel()
        if (query.isBlank()) { state.value = BooksState(); return }
        request = viewModelScope.launch {
            state.value = BooksState(loading = true)
            delay(300)
            try { state.value = BooksState(books = searchBooks(query)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.value = BooksState(error = "Поиск недоступен. Проверьте подключение и повторите запрос.") }
        }
    }
}

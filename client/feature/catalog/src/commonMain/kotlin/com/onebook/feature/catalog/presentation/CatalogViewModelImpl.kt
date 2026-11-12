package com.onebook.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onebook.core.ui.BooksState
import com.onebook.core.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal class CatalogViewModelImpl(private val getBooks: GetBooks) : ViewModel(), CatalogViewModel {
    override val state = MutableStateFlow(BooksState())
    private var request: Job? = null
    override fun refresh() {
        request?.cancel()
        request = viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            try { state.value = BooksState(books = getBooks()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.update { it.copy(loading = false, error = "Не удалось загрузить каталог. Проверьте подключение к серверу.") } }
        }
    }
}

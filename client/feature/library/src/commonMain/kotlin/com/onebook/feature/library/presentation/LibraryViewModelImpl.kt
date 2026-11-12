package com.onebook.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onebook.core.ui.BooksState
import com.onebook.core.domain.AddToLibrary
import com.onebook.core.domain.Book
import com.onebook.core.domain.GetLibrary
import com.onebook.core.domain.RemoveFromLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LibraryViewModelImpl(
    private val getLibrary: GetLibrary,
    private val addToLibrary: AddToLibrary,
    private val removeFromLibrary: RemoveFromLibrary,
) : ViewModel(), LibraryViewModel {
    override val state = MutableStateFlow(BooksState())
    override val message = MutableStateFlow<String?>(null)
    private val mutex = Mutex()
    override fun clearMessage() { message.value = null }
    override fun refresh() = request { }
    override fun add(book: Book) = request { addToLibrary(book); message.value = "Книга добавлена в библиотеку" }
    override fun remove(book: Book) = request { removeFromLibrary(book) }
    private fun request(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                state.update { it.copy(loading = true, error = null) }
                try { action(); state.value = BooksState(books = getLibrary()) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    val error = "Не удалось обновить библиотеку. Повторите попытку."
                    state.update { it.copy(loading = false, error = error) }
                    message.value = error
                }
            }
        }
    }
}

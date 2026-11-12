package com.onebook.feature.search.presentation

import com.onebook.core.ui.BooksState
import kotlinx.coroutines.flow.StateFlow

interface SearchViewModel {
    val query: StateFlow<String>
    val state: StateFlow<BooksState>
    fun search(query: String)
}

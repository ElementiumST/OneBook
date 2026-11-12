package com.onebook.feature.catalog.presentation

import com.onebook.core.ui.BooksState
import kotlinx.coroutines.flow.StateFlow

interface CatalogViewModel {
    val state: StateFlow<BooksState>
    fun refresh()
}

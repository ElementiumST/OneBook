package com.onebook.feature.search.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onebook.feature.search.presentation.SearchViewModelImpl
import com.onebook.feature.search.presentation.SearchViewModel
import org.koin.core.Koin
import org.koin.dsl.module

val searchModule = module {
    factory { SearchViewModelImpl(get()) }
    factory<SearchViewModel> { get<SearchViewModelImpl>() }
}

// The implementation stays in the feature; the platform owns its lifecycle.
fun ViewModelStoreOwner.searchViewModel(koin: Koin): SearchViewModel =
    ViewModelProvider.create(
        owner = this,
        factory = viewModelFactory {
            initializer { koin.get<SearchViewModelImpl>() }
        },
    )[SearchViewModelImpl::class]

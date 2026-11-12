package com.onebook.feature.catalog.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onebook.feature.catalog.presentation.CatalogViewModel
import com.onebook.feature.catalog.presentation.CatalogViewModelImpl
import org.koin.core.Koin
import org.koin.dsl.module

val catalogModule = module {
    factory { CatalogViewModelImpl(get()) }
    factory<CatalogViewModel> { get<CatalogViewModelImpl>() }
}

// The implementation stays in the feature; the platform owns its lifecycle.
fun ViewModelStoreOwner.catalogViewModel(koin: Koin): CatalogViewModel =
    ViewModelProvider.create(
        owner = this,
        factory = viewModelFactory {
            initializer { koin.get<CatalogViewModelImpl>() }
        },
    )[CatalogViewModelImpl::class]

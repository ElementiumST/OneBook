package com.onebook.feature.library.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onebook.feature.library.presentation.LibraryViewModelImpl
import com.onebook.feature.library.presentation.LibraryViewModel
import org.koin.core.Koin
import org.koin.dsl.module

val libraryModule = module {
    factory { LibraryViewModelImpl(get(), get(), get()) }
    factory<LibraryViewModel> { get<LibraryViewModelImpl>() }
}

// The implementation stays in the feature; the platform owns its lifecycle.
fun ViewModelStoreOwner.libraryViewModel(koin: Koin): LibraryViewModel =
    ViewModelProvider.create(
        owner = this,
        factory = viewModelFactory {
            initializer { koin.get<LibraryViewModelImpl>() }
        },
    )[LibraryViewModelImpl::class]

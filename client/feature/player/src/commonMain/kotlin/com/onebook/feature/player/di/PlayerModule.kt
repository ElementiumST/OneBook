package com.onebook.feature.player.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onebook.feature.player.presentation.PlayerViewModelImpl
import com.onebook.feature.player.presentation.PlayerViewModel
import org.koin.core.Koin
import org.koin.dsl.module

val playerModule = module {
    factory { PlayerViewModelImpl(get()) }
    factory<PlayerViewModel> { get<PlayerViewModelImpl>() }
}

// The implementation stays in the feature; the platform owns its lifecycle.
fun ViewModelStoreOwner.playerViewModel(koin: Koin): PlayerViewModel =
    ViewModelProvider.create(
        owner = this,
        factory = viewModelFactory {
            initializer { koin.get<PlayerViewModelImpl>() }
        },
    )[PlayerViewModelImpl::class]

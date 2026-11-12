package com.onebook.core.common.di

import com.onebook.core.common.playback.*
import com.onebook.core.domain.*
import kotlinx.coroutines.*
import org.koin.dsl.module
import org.koin.dsl.onClose

fun commonModule(createEngine: () -> PlaybackEngine) = module {
    factory { GetBooks(get()) }
    factory { SearchBooks(get()) }
    factory { GetLibrary(get()) }
    factory { AddToLibrary(get()) }
    factory { RemoveFromLibrary(get()) }
    factory { GetPlaybackSource(get()) }
    factory { GetListeningPosition(get()) }
    factory { SaveListeningPosition(get()) }
    single<AudioPlayer> {
        CommonAudioPlayer(createEngine(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    } onClose { it?.close() }
}

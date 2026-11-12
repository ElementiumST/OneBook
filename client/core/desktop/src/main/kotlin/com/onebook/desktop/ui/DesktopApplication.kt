package com.onebook.desktop.ui

import androidx.compose.runtime.*
import androidx.compose.ui.window.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.onebook.core.common.navigation.RootComponent
import com.onebook.feature.catalog.di.catalogViewModel
import com.onebook.feature.library.di.libraryViewModel
import com.onebook.feature.player.di.playerViewModel
import com.onebook.feature.search.di.searchViewModel
import javafx.application.Platform
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication

fun runDesktopApplication(graph: KoinApplication) {
    val lifecycle = LifecycleRegistry()
    lateinit var root: RootComponent
    SwingUtilities.invokeAndWait {
        root = RootComponent(DefaultComponentContext(lifecycle))
        lifecycle.resume()
    }
    val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    application {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides owner,
        ) {
            val catalog = remember { owner.catalogViewModel(graph.koin) }
            val search = remember { owner.searchViewModel(graph.koin) }
            val library = remember { owner.libraryViewModel(graph.koin) }
            val player = remember { owner.playerViewModel(graph.koin) }
            val scope = rememberCoroutineScope()
            var closing by remember { mutableStateOf(false) }
            Window(
                title = "OneBook",
                state = rememberWindowState(
                    width = 1100.dp,
                    height = 760.dp,
                ),
                onCloseRequest = {
                    if (!closing) {
                        closing = true
                        scope.launch {
                            player.finishPlayback()
                            owner.viewModelStore.clear()
                            lifecycle.destroy()
                            graph.close()
                            Platform.exit()
                            exitApplication()
                        }
                    }
                },
            ) {
                DesktopContent(
                    root = root,
                    catalog = catalog,
                    search = search,
                    library = library,
                    player = player,
                )
            }
        }
    }
}

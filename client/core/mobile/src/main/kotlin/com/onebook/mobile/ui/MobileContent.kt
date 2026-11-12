package com.onebook.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.onebook.core.common.navigation.RootComponent
import com.onebook.core.common.navigation.Screen
import com.onebook.core.domain.Book
import com.onebook.feature.catalog.presentation.*
import com.onebook.feature.library.presentation.*
import com.onebook.feature.player.presentation.*
import com.onebook.feature.search.presentation.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun MobileContent(
    root: RootComponent,
    catalog: CatalogViewModel,
    search: SearchViewModel,
    library: LibraryViewModel,
    player: PlayerViewModel,
) {
    MaterialTheme {
        val snackbar = remember { SnackbarHostState() }
        val message by library.message.collectAsState()
        LaunchedEffect(
            message,
        ) { message?.let { snackbar.showSnackbar(it); library.clearMessage() } }
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    snackbar,
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 3.dp,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TextButton(
                            onClick = { root.open(Screen.Catalog) },
                        ) {
                            Text(
                                "Каталог",
                            )
                        }
                        TextButton(
                            onClick = { root.open(Screen.Search) },
                        ) {
                            Text(
                                "Поиск",
                            )
                        }
                        TextButton(
                            onClick = { root.open(Screen.Library) },
                        ) {
                            Text(
                                "Мои книги",
                            )
                        }
                        TextButton(
                            onClick = { root.open(Screen.Player) },
                        ) {
                            Text(
                                "Плеер",
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val play: (Book) -> Unit = { player.open(it); root.open(Screen.Player) }
            Children(
                root.stack,
                Modifier.fillMaxSize().padding(padding),
            ) { child ->
                when (child.instance) {
                    Screen.Catalog -> CatalogContent(
                        catalog,
                        play,
                        library::add,
                        Modifier.fillMaxSize(),
                    )
                    Screen.Search -> SearchContent(
                        search,
                        play,
                        library::add,
                        Modifier.fillMaxSize(),
                    )
                    Screen.Library -> LibraryContent(
                        library,
                        play,
                        Modifier.fillMaxSize(),
                    )
                    Screen.Player -> PlayerContent(
                        player,
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MobileContentPreview() {
    val root = remember {
        RootComponent(DefaultComponentContext(LifecycleRegistry()))
    }
    MobileContent(
        root = root,
        catalog = PreviewViewModels.catalog,
        search = PreviewViewModels.search,
        library = PreviewViewModels.library,
        player = PreviewViewModels.player,
    )
}

package com.onebook.desktop.ui

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
fun DesktopContent(
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
        ) { padding ->
            Row(
                Modifier.fillMaxSize().padding(padding),
            ) {
                Surface(
                    Modifier.width(170.dp).fillMaxHeight(),
                    tonalElevation = 3.dp,
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "OneBook",
                            style = MaterialTheme.typography.headlineSmall,
                        )
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
                                "Библиотека",
                            )
                        }
                    }
                }
                val play: (Book) -> Unit = player::open
                Children(
                    root.stack,
                    Modifier.weight(1f).fillMaxHeight(),
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
                        Screen.Player -> Unit
                    }
                }
                VerticalDivider()
                PlayerContent(
                    player,
                    Modifier.width(340.dp).fillMaxHeight(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun DesktopContentPreview() {
    val root = remember {
        RootComponent(DefaultComponentContext(LifecycleRegistry()))
    }
    DesktopContent(
        root = root,
        catalog = PreviewViewModels.catalog,
        search = PreviewViewModels.search,
        library = PreviewViewModels.library,
        player = PreviewViewModels.player,
    )
}

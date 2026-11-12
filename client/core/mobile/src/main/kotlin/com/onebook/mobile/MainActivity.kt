package com.onebook.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import com.onebook.core.common.navigation.RootComponent
import com.onebook.feature.catalog.di.catalogViewModel
import com.onebook.feature.library.di.libraryViewModel
import com.onebook.feature.player.di.playerViewModel
import com.onebook.feature.player.presentation.PlayerViewModel
import com.onebook.feature.search.di.searchViewModel
import com.onebook.mobile.ui.MobileContent

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val koin = (application as OneBookApplication).graph.koin
        val catalog = catalogViewModel(koin)
        val search = searchViewModel(koin)
        val library = libraryViewModel(koin)
        player = playerViewModel(koin)
        val root = RootComponent(defaultComponentContext())
        setContent {
            MobileContent(
                root = root,
                catalog = catalog,
                search = search,
                library = library,
                player = player,
            )
        }
    }

    override fun onStop() {
        player.pauseAndSave()
        super.onStop()
    }
}

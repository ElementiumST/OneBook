package com.onebook.core.common.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import kotlinx.serialization.Serializable

@Serializable
enum class Screen { Catalog, Search, Library, Player }

class RootComponent(context: ComponentContext) : ComponentContext by context {
    private val navigation = StackNavigation<Screen>()
    val stack = childStack(
        source = navigation,
        serializer = Screen.serializer(),
        initialConfiguration = Screen.Catalog,
        handleBackButton = true,
        childFactory = { screen, _ -> screen },
    )
    fun open(screen: Screen) = navigation.bringToFront(screen)
}

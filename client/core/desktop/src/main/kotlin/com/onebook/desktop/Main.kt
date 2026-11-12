package com.onebook.desktop

import com.onebook.desktop.di.createGraph
import com.onebook.desktop.ui.runDesktopApplication
import javafx.application.Platform

fun main() {
    Platform.startup { Platform.setImplicitExit(false) }
    val graph = createGraph(System.getenv("ONEBOOK_API_URL") ?: "http://127.0.0.1:8080")
    runDesktopApplication(graph)
}

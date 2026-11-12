package com.onebook.mobile

import android.app.Application
import com.onebook.mobile.di.createGraph
import org.koin.core.KoinApplication

class OneBookApplication : Application() {
    lateinit var graph: KoinApplication
        private set
    override fun onCreate() {
        super.onCreate()
        graph = createGraph(this, BuildConfig.API_URL)
    }
}

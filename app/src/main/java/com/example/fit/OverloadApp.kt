package com.example.fit

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OverloadApp : Application() {
    /** Replaceable so that tests can run the real activity on an in-memory database. */
    lateinit var container: AppContainer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { created -> appScope.launch { created.initialize() } }
    }
}

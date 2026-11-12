package com.example.fit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fit.AppContainer
import com.example.fit.OverloadApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as OverloadApp).container

/** A ViewModel built from the app's [AppContainer], scoped to the current navigation destination. */
@Composable
inline fun <reified VM : ViewModel> overloadViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

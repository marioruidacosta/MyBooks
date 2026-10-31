package com.mcosta.mybooks.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcosta.mybooks.AppContainer
import com.mcosta.mybooks.ContainerHolder

/** Obtains a ViewModel built from the app's [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM =
    viewModel(factory = viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as ContainerHolder).container) }
    })

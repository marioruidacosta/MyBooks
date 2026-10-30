package com.mcosta.mybooks.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mcosta.mybooks.ui.library.LibraryScreen
import com.mcosta.mybooks.ui.library.LibraryViewModel

object Routes {
    const val LIBRARY = "library"
    const val ADD = "add?isbn={isbn}"
    const val EDIT = "edit/{id}"
    const val BOOK = "book/{id}"
    const val SETTINGS = "settings"

    fun add(isbn: String? = null) = if (isbn == null) "add" else "add?isbn=$isbn"
    fun edit(id: Long) = "edit/$id"
    fun book(id: Long) = "book/$id"
}

@Composable
fun MyBooksNavHost() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    NavHost(nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = appViewModel { LibraryViewModel(it.repository) },
                snackbarHostState = snackbar,
                onOpenBook = { nav.navigate(Routes.book(it)) },
                onAddManually = { nav.navigate(Routes.add()) },
                onScan = {},
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
    }
}

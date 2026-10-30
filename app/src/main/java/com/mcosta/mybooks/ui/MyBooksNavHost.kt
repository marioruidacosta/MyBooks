package com.mcosta.mybooks.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mcosta.mybooks.scan.IsbnScanner
import com.mcosta.mybooks.scan.ScanOutcome
import com.mcosta.mybooks.ui.detail.BookDetailScreen
import com.mcosta.mybooks.ui.detail.BookDetailViewModel
import com.mcosta.mybooks.ui.edit.EditBookScreen
import com.mcosta.mybooks.ui.edit.EditBookViewModel
import com.mcosta.mybooks.ui.library.LibraryScreen
import com.mcosta.mybooks.ui.library.LibraryViewModel
import com.mcosta.mybooks.ui.settings.ContentResolverIo
import com.mcosta.mybooks.ui.settings.SettingsScreen
import com.mcosta.mybooks.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember(context) { IsbnScanner(context) }

    NavHost(nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            val vm = appViewModel { LibraryViewModel(it.repository) }
            LibraryScreen(
                viewModel = vm,
                snackbarHostState = snackbar,
                onOpenBook = { nav.navigate(Routes.book(it)) },
                onAddManually = { nav.navigate(Routes.add()) },
                onScan = {
                    scanner.scan { outcome ->
                        scope.launch {
                            when (outcome) {
                                is ScanOutcome.Isbn -> {
                                    val existing = vm.findByIsbn(outcome.isbn)
                                    nav.navigate(if (existing != null) Routes.book(existing.id) else Routes.add(outcome.isbn))
                                }
                                ScanOutcome.NotAnIsbn -> snackbar.showSnackbar("That barcode isn't a book ISBN")
                                is ScanOutcome.Failed -> snackbar.showSnackbar(outcome.message)
                                ScanOutcome.Cancelled -> Unit
                            }
                        }
                    }
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.ADD,
            arguments = listOf(navArgument("isbn") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val isbn = entry.arguments?.getString("isbn")
            EditBookScreen(
                viewModel = appViewModel { EditBookViewModel(it.repository, bookId = null, isbn = isbn) },
                isNew = true,
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    nav.navigate(Routes.book(id)) { popUpTo(Routes.LIBRARY) }
                },
            )
        }
        composable(Routes.BOOK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("id")
            BookDetailScreen(
                viewModel = appViewModel { BookDetailViewModel(it.repository, id, it.applicationScope) },
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = appViewModel { SettingsViewModel(it.repository, ContentResolverIo(context.contentResolver)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("id")
            EditBookScreen(
                viewModel = appViewModel { EditBookViewModel(it.repository, bookId = id, isbn = null) },
                isNew = false,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
    }
}

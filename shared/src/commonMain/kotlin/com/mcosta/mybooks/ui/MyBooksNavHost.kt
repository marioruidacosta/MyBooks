package com.mcosta.mybooks.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mcosta.mybooks.AppContainer
import com.mcosta.mybooks.platform.ScanOutcome
import com.mcosta.mybooks.social.model.SessionState
import com.mcosta.mybooks.ui.social.AccountRoute
import com.mcosta.mybooks.ui.social.FriendsScreen
import com.mcosta.mybooks.ui.social.FriendsViewModel
import com.mcosta.mybooks.ui.social.ProfileScreen
import com.mcosta.mybooks.ui.social.ProfileViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcosta.mybooks.ui.detail.BookDetailScreen
import com.mcosta.mybooks.ui.detail.BookDetailViewModel
import com.mcosta.mybooks.ui.edit.EditBookScreen
import com.mcosta.mybooks.ui.edit.EditBookViewModel
import com.mcosta.mybooks.ui.library.LibraryScreen
import com.mcosta.mybooks.ui.library.LibraryViewModel
import com.mcosta.mybooks.ui.settings.SettingsScreen
import com.mcosta.mybooks.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val LIBRARY = "library"
    const val ADD = "add?isbn={isbn}"
    const val EDIT = "edit/{id}"
    const val BOOK = "book/{id}"
    const val SETTINGS = "settings"
    const val FRIENDS = "friends"
    const val PROFILE = "profile/{id}"
    const val ACCOUNT = "account"

    fun add(isbn: String? = null) = if (isbn == null) "add" else "add?isbn=$isbn"
    fun edit(id: Long) = "edit/$id"
    fun book(id: Long) = "book/$id"
    fun profile(userId: String) = "profile/$userId"
}

@Composable
fun MyBooksNavHost(container: AppContainer) {
    val social = container.social
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val services = LocalPlatformServices.current

    NavHost(nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            val vm = viewModel { LibraryViewModel(container.repository) }
            LibraryScreen(
                viewModel = vm,
                snackbarHostState = snackbar,
                onOpenBook = { nav.navigate(Routes.book(it)) },
                onAddManually = { nav.navigate(Routes.add()) },
                onScan = {
                    scope.launch {
                        when (val outcome = services.scanner.scan()) {
                            is ScanOutcome.Isbn -> {
                                val existing = vm.findByIsbn(outcome.isbn)
                                nav.navigate(if (existing != null) Routes.book(existing.id) else Routes.add(outcome.isbn))
                            }
                            ScanOutcome.NotAnIsbn -> snackbar.showSnackbar("That barcode isn't a book ISBN")
                            is ScanOutcome.Failed -> snackbar.showSnackbar(outcome.message)
                            ScanOutcome.Cancelled -> Unit
                        }
                    }
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenFriends = social?.let { { nav.navigate(Routes.FRIENDS) } },
            )
        }
        composable(
            Routes.ADD,
            arguments = listOf(navArgument("isbn") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val isbn = entry.savedStateHandle.get<String>("isbn")
            EditBookScreen(
                viewModel = viewModel { EditBookViewModel(container.repository, bookId = null, isbn = isbn) },
                isNew = true,
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    nav.navigate(Routes.book(id)) { popUpTo(Routes.LIBRARY) }
                },
            )
        }
        composable(Routes.BOOK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.savedStateHandle.get<Long>("id")!!
            BookDetailScreen(
                viewModel = viewModel { BookDetailViewModel(container.repository, id, container.applicationScope) },
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
            )
        }
        composable(Routes.SETTINGS) {
            val session = social?.auth?.session?.collectAsStateWithLifecycle()?.value
            SettingsScreen(
                viewModel = viewModel { SettingsViewModel(container.repository, services.documents) },
                onBack = { nav.popBackStack() },
                accountLabel = social?.let {
                    if (session is SessionState.SignedIn) "Your community account" else "Join the MyBooks community"
                },
                onOpenAccount = social?.let { { nav.navigate(Routes.ACCOUNT) } },
            )
        }
        if (social != null) {
            composable(Routes.FRIENDS) {
                FriendsScreen(
                    viewModel = viewModel { FriendsViewModel(social) },
                    onBack = { nav.popBackStack() },
                    onSignIn = { nav.navigate(Routes.ACCOUNT) },
                    onOpenPerson = { nav.navigate(Routes.profile(it)) },
                )
            }
            composable(Routes.PROFILE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val userId = entry.savedStateHandle.get<String>("id")!!
                ProfileScreen(viewModel { ProfileViewModel(social, userId) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.ACCOUNT) {
                AccountRoute(social, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.savedStateHandle.get<Long>("id")!!
            EditBookScreen(
                viewModel = viewModel { EditBookViewModel(container.repository, bookId = id, isbn = null) },
                isNew = false,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
    }
}

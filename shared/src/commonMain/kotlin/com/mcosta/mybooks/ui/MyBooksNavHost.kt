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
import com.mcosta.mybooks.share.SharingState
import com.mcosta.mybooks.social.model.SessionState
import com.mcosta.mybooks.data.releases.NOT_STATED
import com.mcosta.mybooks.data.releases.ReleaseFilter
import com.mcosta.mybooks.data.releases.ReleaseGenre
import com.mcosta.mybooks.ui.releases.ReleaseBookScreen
import com.mcosta.mybooks.ui.releases.ReleaseBookViewModel
import com.mcosta.mybooks.ui.releases.ReleaseGenreScreen
import com.mcosta.mybooks.ui.releases.ReleaseGenreViewModel
import com.mcosta.mybooks.ui.releases.ReleasesScreen
import com.mcosta.mybooks.ui.releases.ReleasesSearchScreen
import com.mcosta.mybooks.ui.releases.ReleasesSearchViewModel
import com.mcosta.mybooks.ui.releases.ReleasesViewModel
import com.mcosta.mybooks.ui.releases.ReleaseView
import io.ktor.http.encodeURLPathPart
import com.mcosta.mybooks.ui.social.AccountRoute
import com.mcosta.mybooks.ui.social.ActivityScreen
import com.mcosta.mybooks.ui.social.ActivityViewModel
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
    const val FRIENDS = "friends?tab={tab}"
    const val ACTIVITY = "activity"
    const val RELEASES = "releases"
    const val RELEASE_GENRE = "releases/{month}/{genre}"
    const val RELEASE_BOOK = "releases/{month}/book/{id}"
    const val RELEASE_AUTHOR = "releases/{month}/author/{name}"
    const val RELEASE_PUBLISHER = "releases/{month}/publisher/{name}"
    const val RELEASE_SEARCH = "releases/search"

    /** The {month} of a release list covering every month. */
    const val ALL_MONTHS = "all"
    const val PROFILE = "profile/{id}"
    const val ACCOUNT = "account"

    fun add(isbn: String? = null) = if (isbn == null) "add" else "add?isbn=$isbn"
    fun edit(id: Long) = "edit/$id"
    fun book(id: Long) = "book/$id"
    fun profile(userId: String) = "profile/$userId"
    fun friends(tab: Int = 0) = "friends?tab=$tab"
    fun releaseGenre(month: String, genre: String) = "releases/$month/$genre"
    fun releaseBook(month: String, id: String) = "releases/$month/book/$id"
    /** An author's or publisher's books; the name is URL-encoded (it may contain spaces, accents or slashes). */
    fun releaseName(month: String, view: ReleaseView, name: String) =
        "releases/$month/${if (view == ReleaseView.AUTHOR) "author" else "publisher"}/${name.encodeURLPathPart()}"
}

@Composable
fun MyBooksNavHost(container: AppContainer) {
    val social = container.social
    val sharing = container.sharingStatus?.state?.collectAsStateWithLifecycle()?.value ?: SharingState()
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
                onOpenFriends = social?.let { { nav.navigate(Routes.friends()) } },
                onOpenActivity = social?.let { { nav.navigate(Routes.ACTIVITY) } },
                onOpenReleases = { nav.navigate(Routes.RELEASES) },
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
                showReview = sharing.hasProfile,
            )
        }
        composable(Routes.BOOK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.savedStateHandle.get<Long>("id")!!
            BookDetailScreen(
                viewModel = viewModel { BookDetailViewModel(container.repository, id, container.applicationScope) },
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
                sharing = sharing,
            )
        }
        composable(Routes.RELEASES) {
            ReleasesScreen(
                viewModel = viewModel { ReleasesViewModel(container.releases) },
                onBack = { nav.popBackStack() },
                onOpenGenre = { month, genre -> nav.navigate(Routes.releaseGenre(month, genre.key)) },
                onOpenName = { month, view, name -> nav.navigate(Routes.releaseName(month, view, name.key)) },
                onSearch = { nav.navigate(Routes.RELEASE_SEARCH) },
            )
        }
        composable(
            Routes.RELEASE_GENRE,
            arguments = listOf(navArgument("month") { type = NavType.StringType }, navArgument("genre") { type = NavType.StringType }),
        ) { entry ->
            val month = entry.savedStateHandle.get<String>("month")!!.takeUnless { it == Routes.ALL_MONTHS }
            val genre = ReleaseGenre.of(entry.savedStateHandle.get<String>("genre")!!)
            ReleaseGenreScreen(
                viewModel = viewModel { ReleaseGenreViewModel(container.releases, month, ReleaseFilter.Genre(genre)) },
                title = genre.label,
                month = month,
                onBack = { nav.popBackStack() },
                onOpenBook = { bookMonth, id -> nav.navigate(Routes.releaseBook(bookMonth, id)) },
            )
        }
        for ((route, view) in listOf(Routes.RELEASE_AUTHOR to ReleaseView.AUTHOR, Routes.RELEASE_PUBLISHER to ReleaseView.PUBLISHER)) {
            composable(
                route,
                arguments = listOf(navArgument("month") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val month = entry.savedStateHandle.get<String>("month")!!.takeUnless { it == Routes.ALL_MONTHS }
                val name = entry.savedStateHandle.get<String>("name")!!
                val filter = if (view == ReleaseView.AUTHOR) ReleaseFilter.Author(name) else ReleaseFilter.Publisher(name)
                val title = when {
                    name != NOT_STATED -> name
                    view == ReleaseView.AUTHOR -> "Author not stated"
                    else -> "Publisher not stated"
                }
                ReleaseGenreScreen(
                    viewModel = viewModel { ReleaseGenreViewModel(container.releases, month, filter) },
                    title = title,
                    month = month,
                    onBack = { nav.popBackStack() },
                    onOpenBook = { bookMonth, id -> nav.navigate(Routes.releaseBook(bookMonth, id)) },
                )
            }
        }
        composable(Routes.RELEASE_SEARCH) {
            ReleasesSearchScreen(
                viewModel = viewModel { ReleasesSearchViewModel(container.releases) },
                onBack = { nav.popBackStack() },
                onOpen = { view, key ->
                    nav.navigate(
                        if (view == ReleaseView.GENRE) Routes.releaseGenre(Routes.ALL_MONTHS, key) else Routes.releaseName(Routes.ALL_MONTHS, view, key),
                    )
                },
            )
        }
        composable(
            Routes.RELEASE_BOOK,
            arguments = listOf(navArgument("month") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val month = entry.savedStateHandle.get<String>("month")!!
            val id = entry.savedStateHandle.get<String>("id")!!
            ReleaseBookScreen(
                viewModel = viewModel { ReleaseBookViewModel(container.releases, container.repository, month, id) },
                onBack = { nav.popBackStack() },
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
            composable(Routes.FRIENDS, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                FriendsScreen(
                    viewModel = viewModel { FriendsViewModel(social) },
                    onBack = { nav.popBackStack() },
                    onSignIn = { nav.navigate(Routes.ACCOUNT) },
                    onOpenPerson = { nav.navigate(Routes.profile(it)) },
                    initialTab = entry.savedStateHandle.get<Int>("tab") ?: 0,
                )
            }
            composable(Routes.ACTIVITY) {
                ActivityScreen(
                    viewModel = viewModel { ActivityViewModel(social, container.repository) },
                    onBack = { nav.popBackStack() },
                    onJoin = { nav.navigate(Routes.ACCOUNT) },
                    onFindPeople = { nav.navigate(Routes.friends(tab = 2)) },
                    onOpenPerson = { nav.navigate(Routes.profile(it)) },
                )
            }
            composable(Routes.PROFILE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val userId = entry.savedStateHandle.get<String>("id")!!
                ProfileScreen(
                    viewModel { ProfileViewModel(social, userId, container.repository) },
                    onBack = { nav.popBackStack() },
                    onOpenPerson = { nav.navigate(Routes.profile(it)) },
                )
            }
            composable(Routes.ACCOUNT) {
                AccountRoute(social, container.sharingStatus, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.savedStateHandle.get<Long>("id")!!
            EditBookScreen(
                viewModel = viewModel { EditBookViewModel(container.repository, bookId = id, isbn = null) },
                isNew = false,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
                showReview = sharing.hasProfile,
            )
        }
    }
}

package com.mcosta.mybooks.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import com.mcosta.mybooks.ui.components.BookCover
import com.mcosta.mybooks.ui.components.RatingStars

private val tabs = listOf(null) + ReadingStatus.entries

fun ReadingStatus?.label() = when (this) {
    null -> "All"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.TO_READ -> "To read"
    ReadingStatus.READ -> "Read"
    ReadingStatus.WISHLIST -> "To buy"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenBook: (Long) -> Unit,
    onAddManually: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Null when the community features aren't configured: no Friends icon. */
    onOpenFriends: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(state.query) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyBooks") },
                actions = {
                    if (onOpenFriends != null) {
                        IconButton(onClick = onOpenFriends) { Icon(Icons.Filled.Group, "Friends") }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.Add, "Add book") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Scan barcode") },
                        leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) },
                        onClick = { menuOpen = false; onScan() },
                    )
                    DropdownMenuItem(
                        text = { Text("Enter manually") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menuOpen = false; onAddManually() },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(state.tab), edgePadding = 8.dp) {
                tabs.forEach { status ->
                    Tab(
                        selected = state.tab == status,
                        onClick = { viewModel.selectTab(status) },
                        text = { Text(status.label()) },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.setQuery(it) },
                placeholder = { Text("Search title or author") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No books here yet", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.books, key = { it.id }) { book ->
                        BookRow(book, onClick = { onOpenBook(book.id) })
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book.coverUrl, Modifier.width(48.dp).height(72.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.authors.isNotBlank()) {
                Text(
                    book.authors,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (book.rating != null) RatingStars(book.rating, onRate = null, size = 16.dp)
            if (book.status == ReadingStatus.READING && book.progressPercent != null) {
                LinearProgressIndicator(
                    progress = { book.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

package com.mcosta.mybooks.ui.releases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcosta.mybooks.data.releases.NameCount
import com.mcosta.mybooks.data.releases.monthLabel

/** Search all months; [onOpen] gets the kind of result (Genre, Author or Publisher) and its key. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesSearchScreen(viewModel: ReleasesSearchViewModel, onBack: () -> Unit, onOpen: (view: ReleaseView, key: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search all months") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Authors, publishers or genres") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            )
            when {
                state.loading -> Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.total == 0) "Loading the months…" else "Loading ${state.loaded} of ${state.total} months…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.total > 0) LinearProgressIndicator(progress = { state.loaded.toFloat() / state.total }, Modifier.fillMaxWidth())
                }
                state.error != null -> Message(state.error!!, onRetry = viewModel::retry)
                else -> {
                    SavedNote(state.savedAt)
                    if (state.missing.isNotEmpty()) MissingNote(state.missing, onRetry = viewModel::retry)
                    Results(state, onOpen)
                }
            }
        }
    }
}

@Composable
private fun Results(state: ReleasesSearchState, onOpen: (ReleaseView, String) -> Unit) {
    val r = state.results
    when {
        state.query.isBlank() -> Hint("Search the authors, publishers and genres of all ${state.months} months.")
        r.isEmpty -> Hint("No authors, publishers or genres match.")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            section("Authors", ReleaseView.AUTHOR, r.authors, onOpen)
            section("Publishers", ReleaseView.PUBLISHER, r.publishers, onOpen)
            section("Genres", ReleaseView.GENRE, r.genres, onOpen)
        }
    }
}

private fun LazyListScope.section(title: String, view: ReleaseView, names: List<NameCount>, onOpen: (ReleaseView, String) -> Unit) {
    if (names.isEmpty()) return
    item(key = "header-$view") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(names, key = { "$view-${it.key}" }) { name ->
        ListItem(
            headlineContent = { Text("${name.label} · ${name.count}") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            modifier = Modifier.clickable { onOpen(view, name.key) },
        )
        HorizontalDivider()
    }
}

/** "Left out, couldn't load: September 2026   Retry" */
@Composable
internal fun MissingNote(missing: List<String>, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Left out, couldn't load: ${missing.joinToString(", ") { monthLabel(it, complete = true) }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

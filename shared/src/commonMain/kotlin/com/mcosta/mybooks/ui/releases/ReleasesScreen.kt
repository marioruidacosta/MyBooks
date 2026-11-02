package com.mcosta.mybooks.ui.releases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcosta.mybooks.data.releases.ReleaseGenre
import com.mcosta.mybooks.data.releases.monthLabel
import com.mcosta.mybooks.ui.formatDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(viewModel: ReleasesViewModel, onBack: () -> Unit, onOpenGenre: (month: String, genre: ReleaseGenre) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New in Portugal") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.months.isEmpty() && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.notPublished -> Message("New releases aren't available yet.")
                state.months.isEmpty() && state.error != null -> Message(state.error!!, onRetry = viewModel::retry)
                else -> Column(Modifier.fillMaxSize()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.months, key = { it.month }) { month ->
                            FilterChip(
                                selected = month.month == state.selected?.month,
                                onClick = { viewModel.select(month.month) },
                                label = { Text(monthLabel(month.month, month.complete)) },
                            )
                        }
                    }
                    Text(
                        "Books added to Portugal's National Bibliography (legal deposit) that month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SavedNote(state.savedAt)
                    val selected = state.selected
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(selected?.genres.orEmpty(), key = { it.first.key }) { (genre, count) ->
                            ListItem(
                                headlineContent = { Text("${genre.label} · $count") },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                                modifier = Modifier.clickable { onOpenGenre(selected!!.month, genre) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** "Showing saved list from 2 Nov, 10:15" when the download failed and the saved copy is shown. */
@Composable
internal fun SavedNote(savedAt: Instant?) {
    if (savedAt == null) return
    Text(
        "Showing saved list from ${formatDateTime(savedAt)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
internal fun Message(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

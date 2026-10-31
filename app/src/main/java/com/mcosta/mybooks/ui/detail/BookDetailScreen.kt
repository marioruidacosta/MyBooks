package com.mcosta.mybooks.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.mcosta.mybooks.ui.components.rememberCoverPhotoTaker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import com.mcosta.mybooks.ui.components.BookCover
import com.mcosta.mybooks.ui.components.RatingStars
import com.mcosta.mybooks.ui.components.StatusSelector
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(viewModel: BookDetailViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val notes by viewModel.notesDraft.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) { if (deleted) onBack() }
    val context = LocalContext.current
    val takePhoto = rememberCoverPhotoTaker(
        onPhoto = viewModel::setCover,
        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
    )
    // Save the notes draft if the app goes to the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNotes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveNotes(); onEdit() }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        val b = book
        if (b == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!deleted) CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(b, onAddPhoto = takePhoto)
            StatusSelector(b.status, viewModel::setStatus, Modifier.fillMaxWidth())
            if (b.status == ReadingStatus.READ && b.dateRead != null) {
                val text = b.dateStarted?.let { "Read from ${formatDate(it)} to ${formatDate(b.dateRead)}" }
                    ?: "Read on ${formatDate(b.dateRead)}"
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (b.status == ReadingStatus.READING) {
                ReadingSection(b, viewModel)
            }
            Column {
                Text("Your score", style = MaterialTheme.typography.labelLarge)
                RatingStars(b.rating, onRate = viewModel::rate, size = 32.dp)
            }
            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::editNotes,
                label = { Text("Notes") },
                minLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) viewModel.saveNotes() },
            )
            b.description?.takeIf { it.isNotBlank() }?.let { Description(it) }
            Text(
                "Added ${formatDate(b.dateAdded)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this book?") },
            text = { Text("It will be removed from your library.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(b: Book, onAddPhoto: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (b.coverUrl.isNullOrBlank()) {
            // No cover found: tap the placeholder to photograph the book.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BookCover(null, Modifier.width(110.dp).height(165.dp).clickable(onClickLabel = "Take cover photo", onClick = onAddPhoto))
                Text("Tap to add a photo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            BookCover(b.coverUrl, Modifier.width(110.dp).height(165.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(b.title, style = MaterialTheme.typography.headlineSmall)
            if (b.authors.isNotBlank()) Text(b.authors, style = MaterialTheme.typography.titleMedium)
            val facts = listOfNotNull(b.publisher, b.publishedYear?.toString(), b.pageCount?.let { "$it pages" })
            if (facts.isNotEmpty()) {
                Text(
                    facts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            b.isbn?.let {
                Text("ISBN $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Description(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.clickable { expanded = !expanded }) {
        Text("Description", style = MaterialTheme.typography.labelLarge)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (!expanded) Text("More", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

internal fun formatDate(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

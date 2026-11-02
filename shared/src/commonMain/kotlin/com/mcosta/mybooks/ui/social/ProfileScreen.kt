package com.mcosta.mybooks.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcosta.mybooks.social.model.FeedEntry
import com.mcosta.mybooks.social.model.Relationship
import com.mcosta.mybooks.social.model.ReportReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onBack: () -> Unit, onOpenPerson: (String) -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var reportingEntry by remember { mutableStateOf<FeedEntry?>(null) }

    LaunchedEffect(state.left) { if (state.left) onBack() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (state.person != null && !state.isMe) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Block") }, onClick = { menuOpen = false; confirmBlock = true })
                                DropdownMenuItem(text = { Text("Report") }, onClick = { menuOpen = false; reporting = true })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.notAvailable) {
                        Text("This profile isn't available", style = MaterialTheme.typography.bodyLarge)
                        return@Column
                    }
                    val person = state.person ?: return@Column
                    PersonAvatar(person.avatarUrl, size = 96.dp)
                    Text(person.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text("@${person.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.isMe) return@Column
                    when (state.relationship) {
                        Relationship.NONE -> Button(onClick = viewModel::add, enabled = !state.busy) { Text("Add friend") }
                        Relationship.REQUEST_SENT -> OutlinedButton(onClick = viewModel::cancel, enabled = !state.busy) { Text("Cancel request") }
                        Relationship.REQUEST_RECEIVED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::decline, enabled = !state.busy) { Text("Decline") }
                            Button(onClick = viewModel::accept, enabled = !state.busy) { Text("Accept") }
                        }
                        Relationship.FRIENDS -> OutlinedButton(onClick = { confirmRemove = true }, enabled = !state.busy) { Text("Friends ✓") }
                    }
                }
            }
            val person = state.person
            if (person != null && !state.notAvailable) {
                item {
                    Text(
                        "Recent activity",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                when {
                    state.activityHidden -> item {
                        Text(
                            "Only ${person.displayName}'s friends can see their activity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    state.activity.isEmpty() && !state.busy -> item {
                        Text(
                            "No activity yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    else -> itemsIndexed(state.activity, key = { _, e -> e.id }) { index, entry ->
                        if (index == state.activity.lastIndex) LaunchedEffect(entry.id) { viewModel.loadMoreActivity() }
                        FeedEntryCard(
                            entry = entry,
                            onPerson = {},
                            onBook = {},
                            onLike = { viewModel.toggleLike(entry.id) },
                            onLikers = { viewModel.showLikers(entry.id) },
                            onReport = if (state.isMe) null else ({ reportingEntry = entry }),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.likers?.let { people ->
        LikersSheet(people, onPerson = { viewModel.dismissLikers(); onOpenPerson(it) }, onDismiss = viewModel::dismissLikers)
    }
    reportingEntry?.let { entry ->
        ReportDialog(
            entry.actor.displayName,
            onSend = { reason, details -> reportingEntry = null; viewModel.report(reason, details, activityId = entry.id) },
            onDismiss = { reportingEntry = null },
        )
    }

    val name = state.person?.displayName.orEmpty()
    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove $name from your friends?",
            text = "You'll stop seeing each other's activity.",
            confirm = "Remove",
            onConfirm = { confirmRemove = false; viewModel.removeFriend() },
            onDismiss = { confirmRemove = false },
        )
    }
    if (confirmBlock) {
        ConfirmDialog(
            title = "Block $name?",
            text = "You'll stop being friends and won't be able to find or see each other. They aren't told.",
            confirm = "Block",
            onConfirm = { confirmBlock = false; viewModel.block() },
            onDismiss = { confirmBlock = false },
        )
    }
    if (reporting) {
        ReportDialog(name, onSend = { reason, details -> reporting = false; viewModel.report(reason, details) }, onDismiss = { reporting = false })
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Reason picker plus optional details (up to 1000 characters). */
@Composable
fun ReportDialog(name: String, onSend: (ReportReason, String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf(ReportReason.SPAM) }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $name") },
        text = {
            Column {
                ReportReason.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == option, onClick = { reason = option }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option.label)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(1000) },
                    label = { Text("Details (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, details) }) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.mcosta.mybooks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * [accountLabel] / [onOpenAccount]: the community Account row ("Join the MyBooks community" or
 * "@username"); both null when the social features aren't configured.
 */
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    accountLabel: String? = null,
    onOpenAccount: (() -> Unit)? = null,
) {
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (accountLabel != null && onOpenAccount != null) {
                Text("Community", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Person, null)
                    Text("  $accountLabel")
                }
                HorizontalDivider()
            }
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Save your whole library, including cover photos, to a .zip file, or restore it " +
                    "(older .json backups work too). Importing merges: matching books are updated, " +
                    "others are added. Nothing is deleted.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = viewModel::export,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Upload, null)
                Text("  Export library")
            }
            OutlinedButton(
                onClick = viewModel::import,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, null)
                Text("  Import library")
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }
}

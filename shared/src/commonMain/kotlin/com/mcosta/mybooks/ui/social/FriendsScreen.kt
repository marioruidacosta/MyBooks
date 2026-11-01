package com.mcosta.mybooks.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcosta.mybooks.social.model.Person
import com.mcosta.mybooks.social.model.Relationship

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(viewModel: FriendsViewModel, onBack: () -> Unit, onSignIn: () -> Unit, onOpenPerson: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Come back from a profile (accept, remove, block…) to fresh lists.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = "Retry", duration = SnackbarDuration.Long)
        viewModel.dismissMessage()
        if (result == SnackbarResult.ActionPerformed) viewModel.refresh()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.signedIn) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Read together", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (state.needsProfile) "Choose a username to finish setting up your community profile."
                        else "Join the MyBooks community to find friends and follow what they read. Your library stays on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onSignIn) { Text(if (state.needsProfile) "Finish setting up" else "Sign in or join") }
                }
                return@Column
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Friends") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                    if (state.incoming.isEmpty()) {
                        Text("Requests")
                    } else {
                        BadgedBox(badge = { Badge { Text("${state.incoming.size}") } }) { Text("Requests") }
                    }
                })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Find people") })
            }
            when (tab) {
                0 -> PeopleList(
                    people = state.friends,
                    empty = "No friends yet. Find people in the third tab.",
                    onOpen = onOpenPerson,
                ) { }
                1 -> LazyColumn(Modifier.fillMaxSize()) {
                    if (state.incoming.isEmpty() && state.outgoing.isEmpty()) {
                        item { EmptyText("No requests") }
                    }
                    if (state.incoming.isNotEmpty()) item { SectionTitle("Asking to be your friend") }
                    items(state.incoming, key = { "in-" + it.id }) { person ->
                        PersonRow(person, onClick = { onOpenPerson(person.id) }) {
                            TextButton(onClick = { viewModel.decline(person.id) }) { Text("Decline") }
                            Button(onClick = { viewModel.accept(person.id) }) { Text("Accept") }
                        }
                    }
                    if (state.outgoing.isNotEmpty()) item { SectionTitle("Sent") }
                    items(state.outgoing, key = { "out-" + it.id }) { person ->
                        PersonRow(person, onClick = { onOpenPerson(person.id) }) {
                            TextButton(onClick = { viewModel.cancel(person.id) }) { Text("Cancel") }
                        }
                    }
                }
                else -> Column {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Name or @username") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (state.query.trim().length >= 2 && state.results.isEmpty()) item { EmptyText("Nobody found") }
                        items(state.results, key = { it.person.id }) { result ->
                            PersonRow(result.person, onClick = { onOpenPerson(result.person.id) }) {
                                when (result.relationship) {
                                    Relationship.FRIENDS -> Text("Friends", color = MaterialTheme.colorScheme.primary)
                                    Relationship.REQUEST_SENT -> Text("Request sent", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Relationship.REQUEST_RECEIVED -> Button(onClick = { viewModel.accept(result.person.id) }) { Text("Accept") }
                                    Relationship.NONE -> Button(onClick = { viewModel.add(result.person.id) }) { Text("Add friend") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleList(people: List<Person>, empty: String, onOpen: (String) -> Unit, actions: @Composable (Person) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (people.isEmpty()) item { EmptyText(empty) }
        items(people, key = { it.id }) { person -> PersonRow(person, onClick = { onOpen(person.id) }) { actions(person) } }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))

@Composable
private fun EmptyText(text: String) = Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Avatar, name and @username, with optional trailing actions. */
@Composable
fun PersonRow(person: Person, onClick: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonAvatar(person.avatarUrl)
        Column(Modifier.weight(1f)) {
            Text(person.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${person.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
    HorizontalDivider()
}

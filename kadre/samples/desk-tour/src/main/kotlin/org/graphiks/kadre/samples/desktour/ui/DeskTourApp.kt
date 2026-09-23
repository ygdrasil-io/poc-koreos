package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.desktour.core.DeskTourState
import org.graphiks.kadre.samples.desktour.core.NoteKey
import org.graphiks.kadre.samples.desktour.core.TourRoute

@Composable
internal fun DeskTourApp(
    state: DeskTourState,
    onCreateNote: () -> Unit,
    onRenameNote: (NoteKey, String) -> Unit,
    onRequestAttention: (NoteKey) -> Unit,
    onToggleDecorations: (NoteKey) -> Unit,
    onSelectRoute: (TourRoute) -> Unit,
    onToggleApiDetails: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // The store holds the only truth (spec §5 : pas de source de vérité concurrente).
    // Material's drawer state follows it, and a scrim dismissal flows back into the store.
    LaunchedEffect(state.apiDetailsOpen) {
        if (state.apiDetailsOpen) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen && state.apiDetailsOpen) onToggleApiDetails()
    }
    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ApiDetailsDrawer(open = state.apiDetailsOpen, entries = state.activity)
            },
        ) {
            Scaffold(
                topBar = { TopAppBar(title = { Text("Kadre Desk Tour") }) },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = state.route == TourRoute.Desk,
                            onClick = { onSelectRoute(TourRoute.Desk) },
                            icon = { Text("Bureau") },
                        )
                        NavigationBarItem(
                            selected = state.route == TourRoute.Activity,
                            onClick = { onSelectRoute(TourRoute.Activity) },
                            icon = { Text("Activité") },
                        )
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    when (state.route) {
                        TourRoute.Desk -> {
                            Text("Espace de travail")
                            Button(onClick = onCreateNote, enabled = state.createNote.enabled) {
                                Text("Créer une note flottante")
                            }
                            if (!state.createNote.enabled) {
                                state.createNote.motif?.let { Text(it) }
                            }
                            state.notes.forEach { note ->
                                var draft by remember(note.key) { mutableStateOf(note.title) }
                                Text(note.title.ifBlank { "Note sans titre" })
                                if (note.mountFailed) {
                                    Text("Cette note n'a pas pu être affichée ; l'hôte courant a refusé le rendu.")
                                }
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    enabled = note.capabilities.canRename,
                                    label = { Text("Titre de la note") },
                                )
                                Button(
                                    onClick = { onRenameNote(note.key, draft) },
                                    enabled = note.capabilities.canRename,
                                ) { Text("Renommer") }
                                if (!note.capabilities.canRename) {
                                    Text("Le renommage n'est pas pris en charge par cette fenêtre.")
                                }
                                Button(
                                    onClick = { onRequestAttention(note.key) },
                                    enabled = note.capabilities.canRequestAttention,
                                ) { Text("Demander l'attention") }
                                Button(
                                    onClick = { onToggleDecorations(note.key) },
                                    enabled = note.capabilities.canChangeDecorations,
                                ) { Text("Décoration") }
                            }
                            state.windows.forEach { window ->
                                Text("${window.title} — ${window.logicalWidth} × ${window.logicalHeight}")
                                Text("Focus : ${window.focusLabel}")
                            }
                        }
                        TourRoute.Activity ->
                            ActivityView(entries = state.activity, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = onToggleApiDetails) { Text("Détails API") }
                }
            }
        }
    }
}

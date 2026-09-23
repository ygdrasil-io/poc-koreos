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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.graphiks.kadre.samples.desktour.core.DeskTourState
import org.graphiks.kadre.samples.desktour.core.TourRoute

@Composable
internal fun DeskTourApp(
    state: DeskTourState,
    onCreateNote: () -> Unit,
    onSelectRoute: (TourRoute) -> Unit,
    onToggleApiDetails: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
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
                            Button(onClick = onCreateNote) { Text("Créer une note flottante") }
                            state.windows.forEach { window ->
                                Text("${window.title} — ${window.logicalWidth} × ${window.logicalHeight}")
                                Text("Focus : ${window.focusLabel}")
                            }
                        }
                        TourRoute.Activity -> ActivityView(entries = state.activity)
                    }
                    Button(onClick = {
                        onToggleApiDetails()
                        scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
                    }) { Text("Détails API") }
                }
            }
        }
    }
}

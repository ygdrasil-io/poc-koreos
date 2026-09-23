package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.desktour.core.DeskTourNote

/**
 * Le contenu d'une note. Le champ reçoit la saisie par la voie d'entrée de Kadre — la scène
 * de la note est alimentée par sa propre surface, pas par une fenêtre Compose parallèle.
 */
@Composable
internal fun NoteContent(note: DeskTourNote, onRename: (String) -> Unit) {
    // Réinitialisé quand le titre change ailleurs : sinon le champ afficherait une valeur périmée.
    var draft by remember(note.key, note.title) { mutableStateOf(note.title) }
    MaterialTheme {
        // Sans fond explicite, le calque Metal de la fenêtre reste apparent.
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            Text(note.title.ifBlank { "Note sans titre" })
            if (note.mountFailed) {
                Text("Cette note n'a pas pu être affichée ; l'hôte courant a refusé le rendu.")
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = note.capabilities.canRename,
                label = { Text("Titre de la note") },
                modifier = Modifier.width(360.dp),
            )
            Button(
                onClick = { onRename(draft) },
                enabled = note.capabilities.canRename && draft != note.title,
            ) { Text("Appliquer") }
            if (!note.capabilities.canRename) {
                Text("Le renommage n'est pas pris en charge par cette fenêtre.")
            }
        }
    }
}

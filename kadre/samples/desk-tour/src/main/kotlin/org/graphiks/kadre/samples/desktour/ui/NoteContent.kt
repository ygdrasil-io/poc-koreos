package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.desktour.core.DeskTourNote

@Composable
internal fun NoteContent(note: DeskTourNote) {
    MaterialTheme {
        // Sans fond explicite, le calque Metal de la fenêtre reste apparent.
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            Text(note.title.ifBlank { "Note sans titre" })
            if (note.mountFailed) {
                Text("Cette note n'a pas pu être affichée ; l'hôte courant a refusé le rendu.")
            }
        }
    }
}

package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.graphiks.kadre.samples.desktour.core.ActivityEntry
import org.graphiks.kadre.samples.desktour.core.ActivityStatus

@Composable
internal fun ActivityView(entries: List<ActivityEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        Text("Aucune activité pour le moment.", modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier) {
        items(entries) { entry ->
            Column {
                Text(entry.label)
                Text(entry.status.readableLabel() + (entry.motif?.let { " — $it" } ?: ""))
                entry.apiDetail?.let { Text("API : $it") }
            }
        }
    }
}

internal fun ActivityStatus.readableLabel(): String = when (this) {
    ActivityStatus.Pending -> "En cours"
    ActivityStatus.Succeeded -> "Terminé"
    ActivityStatus.Rejected -> "Refusé"
    ActivityStatus.Cancelled -> "Annulé"
    ActivityStatus.Unavailable -> "Indisponible"
}

package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.desktour.core.ActivityEntry
import org.graphiks.kadre.samples.desktour.core.ApiDetailKey

@Composable
internal fun ApiDetailsDrawer(open: Boolean, entries: List<ActivityEntry>) {
    // Sans surface opaque, le tiroir laissait lire la page derrière lui et devenait illisible.
    Column(
        Modifier.width(360.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text("Détails API")
        if (!open) {
            Text("Fermé")
            return@Column
        }
        ApiDetailKey.entries.forEach { key ->
            Text(key.userAction)
            key.publicSurface.forEach { symbol -> Text("· $symbol") }
        }
        entries.forEach { entry ->
            entry.apiDetail?.let { Text("$it — ${entry.status.readableLabel()}") }
        }
    }
}

package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.graphiks.kadre.samples.desktour.core.ActivityEntry
import org.graphiks.kadre.samples.desktour.core.ApiDetailKey

@Composable
internal fun ApiDetailsDrawer(open: Boolean, entries: List<ActivityEntry>) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
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

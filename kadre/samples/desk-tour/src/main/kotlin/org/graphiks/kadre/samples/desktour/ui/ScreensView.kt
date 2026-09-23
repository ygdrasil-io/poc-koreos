package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.graphiks.kadre.samples.desktour.core.DisplayPresentation

/**
 * La copie de chaque état. Le §4.3 exige qu'une liste vide ne remplace jamais les états
 * non énumérés, donc chacun a sa phrase.
 */
internal object ScreensViewLabels {
    fun forInventory(inventory: DisplayPresentation?): String = when (inventory) {
        null -> "Les écrans n'ont pas encore été observés."
        is DisplayPresentation.Enumerated ->
            if (inventory.entries.isEmpty()) "Aucun écran n'est rapporté par le host." else ""
        DisplayPresentation.NeedsPermission ->
            "L'inventaire des écrans nécessite une permission qui n'a pas encore été accordée."
        is DisplayPresentation.Denied -> if (inventory.canRequestAgain) {
            "L'accès aux écrans a été refusé ; une nouvelle tentative est possible."
        } else {
            "L'accès aux écrans a été refusé et aucune nouvelle tentative n'est possible depuis l'application."
        }
        is DisplayPresentation.Unavailable -> inventory.motif
    }
}

@Composable
internal fun ScreensView(
    inventory: DisplayPresentation?,
    canRequestAccess: Boolean,
    accessMotif: String?,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("Écrans observés")
        val label = ScreensViewLabels.forInventory(inventory)
        if (label.isNotEmpty()) Text(label)
        if (inventory is DisplayPresentation.Enumerated && inventory.entries.isNotEmpty()) {
            LazyColumn {
                items(inventory.entries) { entry ->
                    Column {
                        Text(entry.name + if (entry.isPrimary) " (principal)" else "")
                        Text("${entry.widthPixels} × ${entry.heightPixels} · échelle ${entry.scaleFactor}")
                        entry.modeLabel?.let { Text(it) }
                    }
                }
            }
        }
        val offerAccess = inventory is DisplayPresentation.NeedsPermission ||
            (inventory is DisplayPresentation.Denied && inventory.canRequestAgain) ||
            (inventory is DisplayPresentation.Unavailable && inventory.retryable)
        if (offerAccess) {
            Button(onClick = onRequestAccess, enabled = canRequestAccess) {
                Text("Autoriser l'accès aux écrans")
            }
            if (!canRequestAccess) accessMotif?.let { Text(it) }
        }
    }
}

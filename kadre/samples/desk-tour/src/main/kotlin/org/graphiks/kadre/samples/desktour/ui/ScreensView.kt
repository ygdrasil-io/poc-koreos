package org.graphiks.kadre.samples.desktour.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.graphiks.kadre.samples.desktour.core.DisplayPresentation
import org.graphiks.kadre.samples.desktour.core.offersAccess

/**
 * La copie de chaque état. Le §4.3 exige qu'une liste vide ne remplace jamais les états
 * non énumérés, donc chacun a sa phrase — et `null` veut dire « rien à dire ».
 */
internal object ScreensViewLabels {
    fun forInventory(inventory: DisplayPresentation?): String? = when (inventory) {
        null -> "Les écrans n'ont pas encore été observés."
        is DisplayPresentation.Enumerated ->
            if (inventory.entries.isEmpty()) "Aucun écran n'est rapporté par le host." else null
        DisplayPresentation.NeedsPermission ->
            "L'inventaire des écrans nécessite une permission qui n'a pas encore été accordée."
        is DisplayPresentation.Denied -> if (inventory.canRequestAgain) {
            "L'accès aux écrans a été refusé ; une nouvelle tentative est possible."
        } else {
            "L'accès aux écrans a été refusé. Aucune nouvelle tentative n'est possible depuis l'application."
        }
        is DisplayPresentation.Unavailable -> if (inventory.retryable) {
            // Surtout pas « échec » : sur cet hôte rien n'a été refusé, l'inventaire n'est
            // simplement pas encore disponible et l'utilisateur doit le demander.
            "L'inventaire des écrans n'est pas encore disponible ; demandez l'accès pour l'obtenir."
        } else {
            inventory.motif
        }
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
        ScreensViewLabels.forInventory(inventory)?.let { Text(it) }
        if (inventory is DisplayPresentation.Enumerated) {
            LazyColumn {
                items(inventory.entries) { entry ->
                    Column {
                        Text(entry.name + if (entry.isPrimary) " (principal)" else "")
                        Text("${entry.widthPixels} × ${entry.heightPixels} · échelle ${entry.scaleFactor}")
                        entry.modeLabel?.let { Text(it) }
                        if (!entry.connected) Text("Déconnecté")
                    }
                }
            }
        }
        if (inventory != null && inventory.offersAccess()) {
            Button(onClick = onRequestAccess, enabled = canRequestAccess) {
                Text("Demander l'accès aux écrans")
            }
            if (!canRequestAccess) accessMotif?.let { Text(it) }
        }
    }
}

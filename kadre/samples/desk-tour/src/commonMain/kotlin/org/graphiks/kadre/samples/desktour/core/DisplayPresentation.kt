package org.graphiks.kadre.samples.desktour.core

import kotlin.math.abs
import kotlin.math.roundToLong
import org.graphiks.kadre.diagnostics.KadreFailure

internal data class DisplayModeLabel(
    val widthPixels: Int,
    val heightPixels: Int,
    val refreshRateHz: Double?,
)

internal data class ScreenEntry(
    val name: String,
    val widthPixels: Int,
    val heightPixels: Int,
    val scaleFactor: Double,
    val modeLabel: String?,
    val isPrimary: Boolean,
    val connected: Boolean = true,
)

/**
 * L'inventaire des écrans tel que la démo le présente. Les états non énumérés sont des
 * états à part entière : le §4.3 exige qu'une liste vide ne les remplace jamais.
 */
internal sealed interface DisplayPresentation {
    data class Enumerated(val entries: List<ScreenEntry>) : DisplayPresentation
    data object NeedsPermission : DisplayPresentation
    data class Denied(val canRequestAgain: Boolean) : DisplayPresentation
    data class Unavailable(val motif: String, val retryable: Boolean = false) : DisplayPresentation
}

/** Vrai quand une action publique permet d'obtenir l'inventaire depuis cet état. */
internal fun DisplayPresentation.offersAccess(): Boolean = when (this) {
    is DisplayPresentation.Enumerated -> false
    DisplayPresentation.NeedsPermission -> true
    is DisplayPresentation.Denied -> canRequestAgain
    is DisplayPresentation.Unavailable -> retryable
}

internal fun screenEntryOf(
    name: String?,
    width: Int,
    height: Int,
    scale: Double,
    mode: DisplayModeLabel?,
    isPrimary: Boolean,
    connected: Boolean = true,
): ScreenEntry = ScreenEntry(
    name = name?.takeIf { it.isNotBlank() } ?: "Écran sans nom",
    widthPixels = width,
    heightPixels = height,
    scaleFactor = scale,
    modeLabel = mode?.let { label ->
        val rate = label.refreshRateHz?.let { hertz ->
            // Un taux de 59,94 Hz ne doit pas s'afficher « 59 Hz » : ce serait une valeur inventée.
            val rendered = if (hertz % 1.0 == 0.0) {
                hertz.toInt().toString()
            } else {
                formatRefreshRate(hertz)
            }
            " @ $rendered Hz"
        } ?: ""
        "${label.widthPixels} × ${label.heightPixels}$rate"
    },
    isPrimary = isPrimary,
    connected = connected,
)

/**
 * Deux décimales au plus pour un taux de rafraîchissement, sans aucune locale : séparateur
 * « . » et chiffres ASCII par construction — le common n'a pas de `String.format`, et le
 * formatage de la plateforme JVM dépend d'elle (`java.util.Locale` n'existe pas ici).
 * L'arrondi est demi-up sur `hertz × 100` ; pour le domaine des taux de rafraîchissement
 * (entiers ou fractions type 59,94 Hz) il rend exactement ce que
 * `String.format(Locale.ROOT, "%.2f", hertz).trimEnd('0').trimEnd('.')` rendait sur JVM.
 * Casse : ce formatage n'en fait jamais — rien à déformer par une locale. Si une casse
 * s'ajoute un jour dans le code partagé, les opérations communes `lowercase()` /
 * `uppercase()` sont invariantes par locale et n'héritent pas du piège du « i turc » de
 * `java.lang.String.toLowerCase()` sous locale tr (le « I » devenant « ı »).
 */
private fun formatRefreshRate(hertz: Double): String {
    val centi = (hertz * 100).roundToLong()
    val sign = if (centi < 0) "-" else ""
    val digits = abs(centi)
    val whole = digits / 100
    val fraction = digits % 100
    return when {
        fraction == 0L -> "$sign$whole"
        fraction % 10 == 0L -> "$sign$whole.${fraction / 10}"
        else -> "$sign$whole.${fraction.toString().padStart(2, '0')}"
    }
}

internal fun displayPresentationFor(failure: KadreFailure): DisplayPresentation = when (failure) {
    // Un refus de permission est un état à part, avec sa propre copie et son propre réessai.
    is KadreFailure.PermissionDenied -> DisplayPresentation.Denied(canRequestAgain = true)
    is KadreFailure.TemporarilyUnavailable ->
        DisplayPresentation.Unavailable(failure.userMotif(), retryable = failure.retryable)
    else -> DisplayPresentation.Unavailable(failure.userMotif())
}

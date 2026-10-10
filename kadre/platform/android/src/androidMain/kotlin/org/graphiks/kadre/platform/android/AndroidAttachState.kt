package org.graphiks.kadre.platform.android

internal class AndroidAttachState {
    private val owners = mutableMapOf<Any, Unit>()
    private val bridges = mutableListOf<String>()
    private var terminated = false

    val isTerminated: Boolean get() = terminated
    val installedBridges: List<String> get() = bridges

    fun recordBridge(name: String) {
        bridges.add(name)
    }

    fun tryClaim(view: Any): Boolean {
        // Pas de garde `terminated` : l'état terminal vide le registre, et l'ownership
        // libéré rend un re-attach possible (le test normatif l'exige) ; seule la
        // libération est figée après la terminaison.
        if (owners.containsKey(view)) return false
        owners[view] = Unit
        return true
    }

    fun release(view: Any): Boolean {
        if (terminated) return false
        return owners.remove(view) != null
    }

    fun markTerminated() {
        terminated = true
        bridges.clear()
        owners.clear()
    }
}

internal fun validateAttachPreconditions(
    isMainThread: Boolean,
    isAttachedToWindow: Boolean,
    alreadyClaimed: Boolean,
): String? = when {
    !isMainThread -> "mainThread"
    !isAttachedToWindow -> "view"
    alreadyClaimed -> "view"
    else -> null
}

/**
 * Registre d'ownership mono-identité de phase 0, indexé par identité de View (roadmap §3.2).
 * Renommé en phase 1 pour céder le nom `AndroidViewOwnership` au registre à double identité
 * (`AndroidViewOwnership.kt`) ; remplacé avec l'API phase 0 en Task 5.
 */
internal object AndroidLegacyViewOwnership {
    private val owners = java.util.concurrent.ConcurrentHashMap<Any, Unit>()

    fun claim(view: Any): Boolean = owners.putIfAbsent(view, Unit) == null
    fun release(view: Any): Boolean = owners.remove(view) != null
    fun isClaimed(view: Any): Boolean = owners.containsKey(view)
}

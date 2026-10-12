package org.graphiks.kadre.internal.wayland

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kffi.wayland.WL_COMPOSITOR_VERSION
import org.graphiks.kffi.wayland.XDG_WM_BASE_VERSION

/** Global du registry : nom d'interface et version annoncée par le compositor. */
internal data class WaylandGlobal(val name: String, val version: Int)

/**
 * Négociation des versions — règle B-4 de `WAYLAND-PHASE-0-KFFI-AUDIT.md` :
 * version bindée = min(annoncée, max supportée par le binding kffi) ;
 * exigence d'admission = annoncée >= minimal requis. Tout écart est une
 * failure avant session (`PlatformFailure(Wayland, "negotiation", …)`),
 * jamais un fonctionnement dégradé.
 */
internal object WaylandNegotiation {

    /** Globals requis dès la tranche 1 (B-4). */
    val REQUIRED_GLOBALS: Map<String, Int> = linkedMapOf(
        "wl_compositor" to 4,
        "xdg_wm_base" to 1,
    )

    /** Plafonds bindés publiés par kffi (constantes `WaylandInterfaces.kt`). */
    val KFFI_MAX_VERSIONS: Map<String, Int> = mapOf(
        "wl_compositor" to WL_COMPOSITOR_VERSION,
        "xdg_wm_base" to XDG_WM_BASE_VERSION,
    )

    fun resolveBinding(advertised: Int, kffiMax: Int): Int = if (advertised < kffiMax) advertised else kffiMax

    /**
     * Vérifie le snapshot du registry : renvoie `null` si l'admission passe,
     * sinon la failure exacte (global manquant ou version sous le minimal).
     */
    fun checkGlobals(snapshot: Collection<WaylandGlobal>): KadreFailure? {
        for ((name, required) in REQUIRED_GLOBALS) {
            val advertised = snapshot.filter { it.name == name }.maxByOrNull { it.version }
            if (advertised == null) {
                return PlatformFailure("missing-$name")
            }
            if (advertised.version < required) {
                return PlatformFailure("$name-advertised-${advertised.version}-below-$required")
            }
        }
        return null
    }

    private fun PlatformFailure(code: String): KadreFailure =
        KadreFailure.PlatformFailure(KadrePlatform.Wayland, "negotiation", code)
}

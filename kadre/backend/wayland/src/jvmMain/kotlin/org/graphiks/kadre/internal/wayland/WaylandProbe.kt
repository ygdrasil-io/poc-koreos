package org.graphiks.kadre.internal.wayland

/**
 * Raison interne de l'indisponibilité du probe (règle R6 de
 * `WAYLAND-PHASE-0-DECISIONS.md`) — diagnostic interne uniquement, jamais publié.
 */
internal enum class WaylandAvailabilityReason {
    Platform,
    WaylandSocketInvalid,
    RuntimeDirAbsent,
    SocketAbsent,
}

/**
 * Environnement du probe, injectable pour les tests : aucune lecture système
 * directe, aucun syscall sur un descripteur (règles R1-R5).
 */
internal data class WaylandProbeEnvironment(
    val isLinux: Boolean,
    val waylandSocket: String?,
    val xdgRuntimeDir: String?,
    val waylandDisplay: String?,
    val socketExists: (String) -> Boolean,
) {
    public companion object {
        public fun fromSystem(): WaylandProbeEnvironment = WaylandProbeEnvironment(
            isLinux = isLinux(),
            waylandSocket = System.getenv("WAYLAND_SOCKET"),
            xdgRuntimeDir = System.getenv("XDG_RUNTIME_DIR"),
            waylandDisplay = System.getenv("WAYLAND_DISPLAY"),
            socketExists = ::isSocketPath,
        )
    }
}

internal data class WaylandProbeResult(
    val available: Boolean,
    val reason: WaylandAvailabilityReason?,
)

/**
 * Probe `isAvailable()` — règles R1-R8 de `WAYLAND-PHASE-0-DECISIONS.md` :
 * environnement seulement, coût borné O(1), aucun fd ouvert, aucun syscall
 * sur le fd `WAYLAND_SOCKET`, totalité (ne lève jamais).
 */
internal object WaylandProbe {

    fun check(env: WaylandProbeEnvironment): WaylandProbeResult {
        if (!env.isLinux) {
            return unavailable(WaylandAvailabilityReason.Platform)
        }
        env.waylandSocket?.takeIf { it.isNotEmpty() }?.let { raw ->
            val fd = raw.toIntOrNull() ?: return unavailable(WaylandAvailabilityReason.WaylandSocketInvalid)
            if (fd < 0) {
                return unavailable(WaylandAvailabilityReason.WaylandSocketInvalid)
            }
            return WaylandProbeResult(available = true, reason = null)
        }
        val display = env.waylandDisplay?.takeIf { it.isNotEmpty() }
        if (display != null && display.startsWith("/")) {
            return resolveSocket(env, display)
        }
        val runtimeDir = env.xdgRuntimeDir?.takeIf { it.isNotEmpty() }
            ?: return unavailable(WaylandAvailabilityReason.RuntimeDirAbsent)
        return resolveSocket(env, "$runtimeDir/${display ?: "wayland-0"}")
    }

    private fun resolveSocket(env: WaylandProbeEnvironment, path: String): WaylandProbeResult {
        return if (env.socketExists(path)) {
            WaylandProbeResult(available = true, reason = null)
        } else {
            unavailable(WaylandAvailabilityReason.SocketAbsent)
        }
    }

    private fun unavailable(reason: WaylandAvailabilityReason) =
        WaylandProbeResult(available = false, reason = reason)
}

/** Attribut POSIX : le chemin existe et n'est ni fichier ni répertoire (socket Unix). Ne lève jamais (R8). */
private fun isSocketPath(path: String): Boolean = try {
    java.nio.file.Files.readAttributes(
        java.nio.file.Path.of(path),
        java.nio.file.attribute.PosixFileAttributes::class.java,
    ).isOther
} catch (_: Exception) {
    false
} catch (_: LinkageError) {
    false
}

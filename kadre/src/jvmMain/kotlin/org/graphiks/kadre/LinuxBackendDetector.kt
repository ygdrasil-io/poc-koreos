/**
 * Detects the appropriate Linux backend (X11 or Wayland) at runtime.
 *
 * Detection strategy (no FFM calls, safe in all contexts):
 *  1. KADRE_LINUX_BACKEND environment variable — explicit override.
 *  2. XDG_SESSION_TYPE / WAYLAND_DISPLAY — current session hint.
 *  3. Reflective load attempt of the available backend classes.
 *
 * Linux X11/Wayland detection.
 * kadre facade extension.
 */
package org.graphiks.kadre

/**
 * Linux backend detector — selects X11 or Wayland depending on the environment.
 *
 * All detections rely on environment variables or
 * [Class.forName]: no FFM calls in the static initializers.
 */
internal object LinuxBackendDetector {

    internal const val X11_CLASS = "org.graphiks.kadre.x11.X11EventLoopKt"
    internal const val WAYLAND_CLASS = "org.graphiks.kadre.wayland.WaylandEventLoopKt"

    /** Resolves the backend class after confirming that it is loadable. */
    fun detectBackendClass(): String = detectBackend().backendClass

    /**
     * Selects a usable backend. In automatic mode each candidate may fall back;
     * an explicit [KADRE_LINUX_BACKEND][KADRE_LINUX_BACKEND] override is strict.
     *
     * [loadClass] and [probe] are seams for isolated tests and for the JVM facade,
     * which invokes the backend as its native availability probe.
     */
    internal fun detectBackend(
        environment: (String) -> String? = System::getenv,
        loadClass: (String) -> Unit = { className ->
            Class.forName(className)
            Unit
        },
        probe: (String) -> Unit = {},
        probeStage: LinuxBackendStage = LinuxBackendStage.PROBE,
    ): LinuxBackendCandidate {
        val override = environment("KADRE_LINUX_BACKEND")?.lowercase()
        val candidates = override?.let { listOf(overrideBackendClass(it)) }
            ?: candidateClasses(environment)
        val failures = mutableListOf<LinuxBackendCandidate>()

        for (backendClass in candidates) {
            try {
                loadClass(backendClass)
            } catch (failure: Throwable) {
                failures += LinuxBackendCandidate(
                    backendClass = backendClass,
                    stage = LinuxBackendStage.CLASSPATH,
                    failure = failure,
                )
                continue
            }

            try {
                probe(backendClass)
                return LinuxBackendCandidate(
                    backendClass = backendClass,
                    stage = probeStage,
                    failure = null,
                )
            } catch (failure: Throwable) {
                failures += LinuxBackendCandidate(
                    backendClass = backendClass,
                    stage = probeStage,
                    failure = failure,
                )
            }
        }

        throw unavailableBackendFailure(
            forcedOverride = override,
            failures = failures,
            environment = environment,
        )
    }

    private fun overrideBackendClass(override: String): String = when (override) {
        "wayland" -> WAYLAND_CLASS
        "x11" -> X11_CLASS
        else -> throw IllegalArgumentException(
            "Invalid KADRE_LINUX_BACKEND: '$override'. Accepted values: 'x11', 'wayland'.",
        )
    }

    private fun candidateClasses(environment: (String) -> String?): List<String> {
        val xdgSession = environment("XDG_SESSION_TYPE")?.lowercase()
        val waylandDisplay = environment("WAYLAND_DISPLAY")
        return if (xdgSession == "wayland" || waylandDisplay != null) {
            listOf(WAYLAND_CLASS, X11_CLASS)
        } else {
            listOf(X11_CLASS, WAYLAND_CLASS)
        }
    }

    private fun unavailableBackendFailure(
        forcedOverride: String?,
        failures: List<LinuxBackendCandidate>,
        environment: (String) -> String?,
    ): IllegalStateException {
        val primary = failures.firstOrNull()?.failure
        val attempts = failures.joinToString(separator = "; ") { candidate ->
            "backend=${candidate.backendClass} stage=${candidate.stage.name.lowercase()} " +
                "cause=${candidate.failure?.javaClass?.simpleName}: ${candidate.failure?.message}"
        }
        val context = "[WAYLAND_DISPLAY=${environment("WAYLAND_DISPLAY")}, " +
            "DISPLAY=${environment("DISPLAY")}, " +
            "XDG_SESSION_TYPE=${environment("XDG_SESSION_TYPE")}]"
        val policy = if (forcedOverride == null) {
            "No usable Linux backend was found"
        } else {
            "Forced Linux backend '$forcedOverride' failed"
        }
        return IllegalStateException("$policy. $attempts $context", primary).also { aggregate ->
            failures.drop(1).forEach { candidate ->
                candidate.failure?.let(aggregate::addSuppressed)
            }
        }
    }

    /**
     * Checks whether a class is reachable on the current classpath.
     *
     * Uses [Class.forName] and catches any [Throwable] (covers
     * [ClassNotFoundException], [LinkageError], [ExceptionInInitializerError],
     * [UnsatisfiedLinkError]) per rule PR #49 §1B.
     *
     * @param className Fully qualified name of the class to test.
     * @param debug If `true`, prints a diagnostic message on failure.
     * @return `true` if the class is loadable, `false` otherwise.
     */
    internal fun canLoad(className: String, debug: Boolean = false): Boolean {
        return try {
            Class.forName(className)
            true
        } catch (e: Throwable) {
            if (debug) println("[kadre-debug] Cannot load $className: ${e::class.simpleName}: ${e.message}")
            false
        }
    }
}

/** The lifecycle point at which a Linux backend candidate was evaluated. */
internal enum class LinuxBackendStage {
    CLASSPATH,
    PROBE,
    LAUNCH,
}

/** Outcome of one Linux backend candidate evaluation. */
internal data class LinuxBackendCandidate(
    val backendClass: String,
    val stage: LinuxBackendStage,
    val failure: Throwable?,
)

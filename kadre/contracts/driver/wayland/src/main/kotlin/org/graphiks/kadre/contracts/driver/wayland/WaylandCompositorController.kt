package org.graphiks.kadre.contracts.driver.wayland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import kotlin.io.path.exists

/**
 * Contrôle du compositor de test (stimuli externes, topologie §4) : démarre
 * un Weston headless isolé selon la baseline B-5, ou réutilise le compositor
 * fourni par l'environnement (`WAYLAND_DISPLAY` déjà défini — mode CI).
 *
 * Recette B-5 : `weston --backend=headless-backend.so --renderer=pixman
 * --width=800 --height=600 --socket=<dédié> --idle-time=0`, `XDG_RUNTIME_DIR`
 * dédié chmod 0700, attente du socket, journal dans le build.
 */
public class WaylandCompositorController(
    public val socketName: String = "kadre-wayland-test",
    private val runtimeDirectory: Path = Path.of(System.getProperty("java.io.tmpdir"), "kadre-wayland-test"),
    private val logDirectory: Path = Path.of(System.getProperty("java.io.tmpdir"), "kadre-wayland-test-logs"),
) : AutoCloseable {

    private var process: Process? = null
    private var ownsCompositor = false

    public val displayName: String
        get() = externalDisplayName ?: socketName

    /**
     * Adresse de socket à consommer par le client : chemin absolu en mode
     * auto-hébergé (indépendant de `XDG_RUNTIME_DIR`), nom relatif en mode
     * externalisé (le JVM hérite alors du `XDG_RUNTIME_DIR` du script).
     */
    public val socketAddress: String
        get() = if (ownsCompositor) {
            runtimeDirectory.resolve(displayName).toString()
        } else {
            displayName
        }

    private var externalDisplayName: String? = null

    public fun start(): Unit {
        val external = System.getenv("WAYLAND_DISPLAY")?.takeIf { it.isNotBlank() }
        val externalRuntime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
        if (external != null && externalRuntime != null) {
            externalDisplayName = external
            ownsCompositor = false
            return
        }
        Files.createDirectories(runtimeDirectory)
        Files.setPosixFilePermissions(
            runtimeDirectory,
            PosixFilePermissions.fromString("rwx------"),
        )
        Files.createDirectories(logDirectory)
        val logFile = logDirectory.resolve("weston.log")
        val builder = ProcessBuilder(
            "weston",
            "--backend=headless-backend.so",
            "--renderer=pixman",
            "--width=800",
            "--height=600",
            "--socket=$socketName",
            "--idle-time=0",
        )
        builder.environment()["XDG_RUNTIME_DIR"] = runtimeDirectory.toString()
        builder.redirectErrorStream(true)
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
        val started = builder.start()
        process = started
        ownsCompositor = true
        val socket = runtimeDirectory.resolve(socketName)
        val deadline = Duration.ofSeconds(60)
        val began = System.nanoTime()
        while (socket.exists().not()) {
            if (started.isAlive.not()) {
                error("weston exited before publishing its socket; see $logFile")
            }
            if ((System.nanoTime() - began) > deadline.toNanos()) {
                started.destroy()
                error("weston did not publish $socket within 60s; see $logFile")
            }
            Thread.sleep(250)
        }
        externalDisplayName = null
    }

    /** Environnement à poser pour le JVM sous test (mode compositor lancé ici). */
    public fun environment(): Map<String, String> = buildMap {
        put("WAYLAND_DISPLAY", displayName)
        put("XDG_RUNTIME_DIR", runtimeDirectory.toString())
    }

    override fun close() {
        if (ownsCompositor) {
            process?.destroy()
            process?.waitFor()
            process = null
            ownsCompositor = false
        }
        externalDisplayName = null
    }
}

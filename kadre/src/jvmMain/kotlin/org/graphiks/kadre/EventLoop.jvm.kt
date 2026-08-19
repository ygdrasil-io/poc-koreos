/**
 * JVM implementation of the kadre event loop — selects the backend
 * based on the host operating system.
 *
 * On macOS → delegates to [org.graphiks.kadre.appkit.AppKitEventLoopKt.runApp]
 *   (kadre-appkit, direct dependency of jvmMain).
 * On Windows → delegates to [org.graphiks.kadre.win32.Win32EventLoopKt.runApp]
 *   (kadre-win32, loaded via reflection — lazy loading, no direct import).
 * On Linux → delegates to X11 or Wayland according to [LinuxBackendDetector]
 *   (kadre-x11 / kadre-wayland, loaded via reflection).
 *
 * Lazy loading via reflection ensures that native bindings (user32.dll,
 * libX11, libwayland…) are never initialized on another OS, even if
 * several backends are present on the classpath (multi-OS build).
 *
 * GRA-129: KMP facade — actual jvmMain.
 * Windows EventLoop facade.
 * Linux X11/Wayland backend detection.
 * Linux kadre facade extension.
 */
package org.graphiks.kadre

import java.lang.reflect.InvocationTargetException

/**
 * JVM implementation of [EventLoop].
 *
 * Routes to the appropriate backend via reflection:
 * - macOS   → `org.graphiks.kadre.appkit.AppKitEventLoopKt#runApp`
 * - Windows → `org.graphiks.kadre.win32.Win32EventLoopKt#runApp`
 * - Linux   → X11 or Wayland according to [LinuxBackendDetector]
 *
 * No direct import of the backend modules — loading is deferred to
 * runtime to avoid initializing native bindings on the wrong OS.
 */
actual class EventLoop actual constructor() {

    /**
     * Starts the event loop and delegates callbacks to the provided handler.
     *
     * Blocking — only returns when the application closes.
     *
     * @param handler Handler for the application's lifecycle and events.
     * @throws UnsupportedOperationException if the OS is not supported or if the
     *   corresponding backend cannot be found on the classpath.
     */
    actual fun runApp(handler: ApplicationHandler) {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("linux")) {
            runLinuxBackend(handler)
            return
        }

        val (backendClass, platform) = when {
            os.contains("mac") -> "org.graphiks.kadre.appkit.AppKitEventLoopKt" to "macOS"
            os.contains("win") -> "org.graphiks.kadre.win32.Win32EventLoopKt" to "Windows"
            else -> throw UnsupportedOperationException(
                "Operating system not supported by kadre-jvm: '$os'. " +
                "Supported platforms: macOS, Windows, Linux."
            )
        }

        invokeBackendRunApp(backendClass, handler, platform)
    }
}

/**
 * Probes Linux backends before application startup, then launches exactly the
 * selected backend. Launch failures are intentionally not candidates for fallback.
 */
internal fun runLinuxBackend(
    handler: ApplicationHandler,
    environment: (String) -> String? = System::getenv,
    loadClass: (String) -> Unit = { backendClass ->
        Class.forName(backendClass)
        Unit
    },
    probe: (String) -> Unit = ::invokeLinuxBackendProbe,
    launch: (String, ApplicationHandler) -> Unit = { backendClass, applicationHandler ->
        invokeBackendRunApp(backendClass, applicationHandler, linuxBackendPlatform(backendClass))
    },
) {
    val selection = LinuxBackendDetector.detectBackend(
        environment = environment,
        loadClass = loadClass,
        probe = probe,
        probeStage = LinuxBackendStage.PROBE,
    )
    launch(selection.backendClass, handler)
}

/** Invokes the native connection probe exported by a Linux backend. */
internal fun invokeLinuxBackendProbe(backendClass: String) {
    val klass = Class.forName(backendClass)
    val method = klass.getMethod("probeConnection")
    try {
        method.invoke(null)
    } catch (failure: InvocationTargetException) {
        rethrowInvocationTarget(
            failure = failure,
            operation = "probe",
            platform = linuxBackendPlatform(backendClass),
            backendClass = backendClass,
        )
    }
}

/** Invokes a backend entry point while preserving its native failure as the primary cause. */
internal fun invokeBackendRunApp(
    backendClass: String,
    handler: ApplicationHandler,
    platform: String,
) {
    val klass = try {
        Class.forName(backendClass)
    } catch (failure: ClassNotFoundException) {
        throw UnsupportedOperationException(
            "$backendClass not found on classpath. Add the corresponding kadre backend dependency.",
            failure,
        )
    }

    val method = klass.getMethod("runApp", ApplicationHandler::class.java)
    try {
        method.invoke(null, handler)
    } catch (failure: InvocationTargetException) {
        rethrowInvocationTarget(
            failure = failure,
            operation = "launch",
            platform = platform,
            backendClass = backendClass,
        )
    }
}

private fun linuxBackendPlatform(backendClass: String): String = when (backendClass) {
    LinuxBackendDetector.X11_CLASS -> "X11"
    LinuxBackendDetector.WAYLAND_CLASS -> "Wayland"
    else -> "Linux"
}

private fun rethrowInvocationTarget(
    failure: InvocationTargetException,
    operation: String,
    platform: String,
    backendClass: String,
): Nothing {
    val target = failure.targetException
    target.addSuppressed(
        IllegalStateException("Failed to $operation $platform backend $backendClass through reflection"),
    )
    throw target
}

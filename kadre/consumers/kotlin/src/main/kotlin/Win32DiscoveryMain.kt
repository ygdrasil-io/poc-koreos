import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.DesktopIntegration
import org.graphiks.kadre.platform.desktop.attachKadreDesktop
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec

/**
 * Consumer d'exécution win32 (phase 13) : découverte ServiceLoader du
 * provider win32 via l'umbrella PUBLIÉ (jamais project(...)), session
 * standalone avec fenêtre réelle, et refusal normatif exact de la combinaison
 * non construite (AppKitMainLoop × win32). Exit 0 = preuve.
 */
public fun main() {
    if (!System.getProperty("os.name").startsWith("Windows")) {
        println("win32-consumer: skipped on non-Windows")
        return
    }
    val opened = java.util.concurrent.atomic.AtomicBoolean(false)
    val outcome = runKadreApplication(
        options = DesktopHostOptions.Standalone(backend = DesktopBackend.Win32),
        application = KadreApplication {
            val requested = windows.requestWindow(
                WindowSpec(title = "kadre win32 consumer", contentSize = LogicalSize(240.0, 160.0)),
            )
            check(requested is KadreResult.Success<*>) { "window request failed: $requested" }
            (requested as KadreResult.Success<*>).value.let { request ->
                if (request is org.graphiks.kadre.window.WindowRequest) {
                    val done = java.util.concurrent.CountDownLatch(1)
                    val watcher = Thread({
                        try {
                            kotlinx.coroutines.runBlocking {
                                kotlinx.coroutines.withTimeout(20_000) { request.await() }
                            }
                        } finally {
                            done.countDown()
                        }
                    }, "kadre-consumer-window")
                    watcher.isDaemon = true
                    watcher.start()
                    done.await(25, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            opened.set(true)
            requestStop()
        },
        policy = KadrePolicies.Default,
    )
    check(opened.get()) { "the consumer window never opened" }
    check(
        outcome == SessionOutcome.Completed || outcome is SessionOutcome.Stopped,
    ) { "unexpected outcome: $outcome" }

    // La combinaison non construite conserve la failure exacte de la façade.
    val scope = CoroutineScope(Job())
    val embedded = scope.attachKadreDesktop(
        KadreApplicationFactory { KadreApplication { requestStop() } },
        DesktopHostOptions.Embedded(
            integration = DesktopIntegration.AppKitMainLoop,
            backend = DesktopBackend.Win32,
        ),
    )
    check(embedded is KadreResult.Failure) { "AppKitMainLoop must refuse on win32" }
    scope.cancel()
    println("win32-consumer: ok ($outcome)")
    exitProcess(0)
}

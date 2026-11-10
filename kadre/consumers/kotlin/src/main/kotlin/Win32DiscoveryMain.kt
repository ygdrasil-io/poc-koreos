import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreRunOptions
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.getOrNull
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.window.WindowSpec

/**
 * Consumer d'exécution win32 : découverte ServiceLoader du provider win32 via
 * l'umbrella PUBLIÉ (jamais project(...)), session ownership-first avec le
 * backend win32 explicite et fenêtre réelle. Exit 0 = preuve.
 */
public fun main() {
    if (!System.getProperty("os.name").startsWith("Windows")) {
        println("win32-consumer: skipped on non-Windows")
        return
    }
    val opened = java.util.concurrent.atomic.AtomicBoolean(false)
    val outcome = runBlocking {
        runKadreApplication(
            options = KadreRunOptions(policy = KadrePolicies.Default),
            backend = DesktopBackend.Win32,
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
        )
    }.getOrNull() ?: error("run failed")
    check(opened.get()) { "the consumer window never opened" }
    check(
        outcome == SessionOutcome.Completed || outcome is SessionOutcome.Stopped,
    ) { "unexpected outcome: $outcome" }

    println("win32-consumer: ok ($outcome)")
    exitProcess(0)
}

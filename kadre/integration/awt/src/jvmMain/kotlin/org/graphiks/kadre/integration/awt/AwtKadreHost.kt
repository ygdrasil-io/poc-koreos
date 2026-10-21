package org.graphiks.kadre.integration.awt

import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.platform.desktop.DesktopBackend
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.DesktopIntegration
import org.graphiks.kadre.platform.desktop.attachKadreDesktop
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.policy.KadrePolicies

/**
 * Consumer-side bridge for embedding a kadre session into an existing AWT /
 * Swing application on Windows (closure of the win32 backend, phase 12).
 *
 * The calling thread MUST be the AWT EventDispatchThread: the win32 provider
 * creates its windows on the calling thread and drains their messages
 * through bounded EDT ticks — this helper enforces that contract up front
 * (`EventQueue.isDispatchThread`), so a caller error surfaces as a typed
 * [IllegalStateException] before any session state exists, instead of a
 * half-attached session.
 *
 * The host loop keeps ownership: closing the returned session (or cancelling
 * [scope]) detaches kadre without posting `WM_QUIT` to the AWT queue, and the
 * last kadre window closing never stops an embedded session.
 */
public object AwtKadreHost {

    /** Attaches one kadre session to the running AWT loop, on the EDT. */
    public fun attach(
        scope: CoroutineScope,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
        allowUserAttention: Boolean = false,
    ): KadreResult<KadreSession> {
        if (!java.awt.EventQueue.isDispatchThread()) {
            throw IllegalStateException(
                "AwtKadreHost.attach must run on the AWT EventDispatchThread — " +
                    "wrap the call in EventQueue.invokeLater { ... }",
            )
        }
        return scope.attachKadreDesktop(
            applicationFactory,
            DesktopHostOptions.Embedded(
                integration = DesktopIntegration.AwtEventDispatchThread,
                backend = DesktopBackend.Win32,
                allowUserAttention = allowUserAttention,
            ),
            policy,
        )
    }
}

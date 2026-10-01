package org.graphiks.kadre.platform.web

import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreLaunchContext
import org.graphiks.kadre.application.KadreLaunchReason
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.window.WindowCreationMode
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLIFrameElement
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsWebAttachTest {
    @Test
    fun manualPolicyAcceptsAnInitiallyDisconnectedHost() = runTest {
        val host = document.createElement("div") as HTMLElement
        val scopeReady = CompletableDeferred<KadreScope>()

        val session = assertIs<KadreResult.Success<KadreSession>>(
            host.attachKadre(this, attachmentPolicy = WebAttachmentPolicy.Manual) {
                scopeReady.complete(this)
                awaitCancellation()
            },
        ).value
        testScheduler.runCurrent()

        assertNotNull(scopeReady.await().primarySurface.value)

        session.requestStop()
        testScheduler.runCurrent()
    }

    @Test
    fun duplicateAttachUsesTheHostElementAsItsStableIdentity() = runTest {
        val host = existingHostElement()
        val owner = assertIs<KadreResult.Success<KadreSession>>(
            host.attachKadre(this) { awaitCancellation() },
        ).value

        assertEquals(
            KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host)),
            host.attachKadre(this) { awaitCancellation() },
        )

        owner.requestStop()
        testScheduler.runCurrent()
        host.remove()
    }

    @Test
    fun applicationOverloadAttachesToTheExistingHostWithoutCreatingAnotherElement() = runTest {
        val host = existingHostElement()
        val before = document.getElementsByTagName("*").length
        val scopeReady = CompletableDeferred<KadreScope>()

        val session = assertIs<KadreResult.Success<KadreSession>>(
            host.attachKadre(this, attachmentPolicy = WebAttachmentPolicy.StopWhenDetached) {
                scopeReady.complete(this)
                awaitCancellation()
            },
        ).value
        testScheduler.runCurrent()
        val scope = scopeReady.await()

        assertEquals(before, document.getElementsByTagName("*").length)
        assertNotNull(scope.primarySurface.value)
        assertEquals(LogicalSize(320.0, 180.0), scope.primarySurface.value!!.state.value.logicalSize)
        assertEquals(PhysicalSize(320, 180), scope.primarySurface.value!!.state.value.physicalSize)
        assertNull(scope.windows.state.value.primary)
        assertTrue(scope.windows.state.value.windows.isEmpty())

        session.requestStop()
        testScheduler.runCurrent()
        assertEquals(SurfaceAttachmentState.Detached, scope.primarySurface.value!!.state.value.attachment)
        host.remove()
    }

    @Test
    fun factoryOverloadAttachesToTheExistingHost() = runTest {
        val host = existingHostElement()
        val before = document.getElementsByTagName("*").length
        var providerCalled = false
        val scopeReady = CompletableDeferred<KadreScope>()
        val factory = KadreApplicationFactory {
            KadreApplication {
                scopeReady.complete(this)
                awaitCancellation()
            }
        }

        val session = assertIs<KadreResult.Success<KadreSession>>(
            host.attachKadre(
                this,
                factory,
                attachmentPolicy = WebAttachmentPolicy.Manual,
                windowProvider = WebWindowProvider { _, _ ->
                    providerCalled = true
                    error("the provider answers requestWindow, never the attach")
                },
            ),
        ).value
        testScheduler.runCurrent()
        val scope = scopeReady.await()

        // The attach itself stays provider-free and creates nothing: configuring a provider only
        // arms the window capability, it never opens a thing on its own.
        assertEquals(before, document.getElementsByTagName("*").length)
        assertEquals(false, providerCalled)
        val capability = assertIs<Capability.Supported<Set<WindowCreationMode>>>(
            scope.windows.state.value.capabilities.requestWindow,
        )
        assertEquals(setOf(WindowCreationMode.OpenedInNewSession), capability.constraints)

        // The wired provider does answer `requestWindow`, and the exception it throws becomes the
        // closed failure the contract promises — an outcome of the admitted request.
        val request = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
            scope.windows.requestWindow(WindowSpec()),
        ).value
        assertEquals(
            WindowRequestOutcome.Rejected(
                KadreFailure.PlatformFailure(KadrePlatform.Web, "WebWindowProvider", "callback-exception"),
            ),
            request.await(),
        )
        assertEquals(true, providerCalled)
        session.requestStop()
        host.remove()
    }

    @Test
    fun theConfiguredProviderOpensAChildSessionInADistinctBrowsingContext() = runTest {
        // The host prepared, before Kadre ever ran, the second browsing context an offer must live
        // in: the iframe is its own window. Kadre is handed an element that already exists.
        val host = existingHostElement()
        val iframe = document.createElement("iframe") as HTMLIFrameElement
        document.body!!.appendChild(iframe)
        val childDocument = assertNotNull(iframe.contentDocument)
        // The element belongs to the offered browsing context: an `as` cast would test instanceof
        // against this window's HTMLElement constructor and fail the very cross-realm element the
        // contract requires, so the target-known type is asserted unchecked.
        val childElement = childDocument.createElement("div").unsafeCast<HTMLElement>().also {
            it.style.width = "160px"
            it.style.height = "120px"
            childDocument.body!!.appendChild(it)
        }

        val launches = mutableListOf<KadreLaunchContext>()
        val hostScopeReady = CompletableDeferred<KadreScope>()
        val childScopeReady = CompletableDeferred<KadreScope>()
        val factory = KadreApplicationFactory { context ->
            launches += context
            KadreApplication {
                if (context.reason == KadreLaunchReason.AdditionalHostRequested) {
                    childScopeReady.complete(this)
                } else {
                    hostScopeReady.complete(this)
                }
                awaitCancellation()
            }
        }
        // The offer's scope is the host's own choice and is no child of the requester's: its
        // lifetime never follows the requester's.
        val childScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        val session = assertIs<KadreResult.Success<KadreSession>>(
            host.attachKadre(
                this,
                factory,
                attachmentPolicy = WebAttachmentPolicy.Manual,
                windowProvider = WebWindowProvider { _, _ ->
                    KadreResult.Success(WebWindowHost(childElement, childScope, WebAttachmentPolicy.Manual))
                },
            ),
        ).value
        testScheduler.runCurrent()
        val hostScope = hostScopeReady.await()

        // One request, one child session, opened in the context the host offered — the outcome this
        // target owes, and the launch identity that says why the child was launched.
        val request = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
            hostScope.windows.requestWindow(WindowSpec()),
        ).value
        val opened = assertIs<WindowRequestOutcome.OpenedInNewSession>(request.await())
        assertNotEquals(session.id, opened.sessionId)
        testScheduler.runCurrent()
        val childLaunch = launches.last()
        assertEquals(KadreLaunchReason.AdditionalHostRequested, childLaunch.reason)
        assertEquals(request.id, childLaunch.originatingRequestId)
        assertEquals(opened.sessionId, childLaunch.sessionId)
        val childAppScope = childScopeReady.await()

        // The requester stops; the child keeps its own scope and keeps running.
        session.requestStop()
        testScheduler.runCurrent()
        assertFalse(childAppScope.coroutineContext[Job]!!.isCancelled)

        childScope.cancel()
        iframe.remove()
        host.remove()
    }

    private fun existingHostElement(): HTMLElement =
        (document.createElement("div") as HTMLElement).also {
            it.style.width = "320px"
            it.style.height = "180px"
            document.body!!.appendChild(it)
        }
}

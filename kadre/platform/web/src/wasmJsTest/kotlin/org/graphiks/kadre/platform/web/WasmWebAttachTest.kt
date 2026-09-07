package org.graphiks.kadre.platform.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.browser.document
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.KadreScope
import org.graphiks.kadre.application.KadreSession
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.window.WindowRequestOutcome
import org.graphiks.kadre.window.WindowSpec
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WasmWebAttachTest {
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
                    error("phase 0 must not call WebWindowProvider")
                },
            ),
        ).value
        testScheduler.runCurrent()
        val scope = scopeReady.await()
        val request = assertIs<KadreResult.Success<org.graphiks.kadre.window.WindowRequest>>(
            scope.windows.requestWindow(WindowSpec()),
        ).value

        assertEquals(before, document.getElementsByTagName("*").length)
        assertEquals(
            WindowRequestOutcome.Rejected(KadreFailure.Unsupported(KadreOperation.RequestWindow)),
            request.await(),
        )
        assertEquals(false, providerCalled)
        session.requestStop()
        host.remove()
    }

    private fun existingHostElement(): HTMLElement =
        (document.createElement("div") as HTMLElement).also(document.body!!::appendChild)
}

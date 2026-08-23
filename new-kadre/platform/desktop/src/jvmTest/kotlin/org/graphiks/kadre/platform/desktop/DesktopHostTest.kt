package org.graphiks.kadre.platform.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.diagnostics.KadreException
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopHostTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val embedded = DesktopHostOptions.Embedded(DesktopIntegration.AppKitMainLoop)
    private val standalone = DesktopHostOptions.Standalone(DesktopBackend.AppKit)
    private val unsupported = KadreFailure.Unsupported(KadreOperation.HostAttach)

    @Test
    fun factoryAttachIsExplicitlyUnsupportedBeforeRuntimeExists() {
        var factoryInvoked = false
        val result = scope.attachKadreDesktop(
            applicationFactory = KadreApplicationFactory {
                factoryInvoked = true
                KadreApplication { }
            },
            options = embedded,
        )

        assertEquals(KadreResult.Failure(unsupported), result)
        assertEquals(false, factoryInvoked)
    }

    @Test
    fun directAttachIsExplicitlyUnsupportedBeforeRuntimeExists() {
        var applicationInvoked = false
        val result = scope.attachKadreDesktop(embedded) {
            applicationInvoked = true
        }

        assertEquals(KadreResult.Failure(unsupported), result)
        assertEquals(false, applicationInvoked)
    }

    @Test
    fun standaloneFactoryFailureThrowsBeforeCreatingApplication() {
        var factoryInvoked = false

        val exception = assertFailsWith<KadreException> {
            runKadreApplication(
                applicationFactory = KadreApplicationFactory {
                    factoryInvoked = true
                    KadreApplication { }
                },
                options = standalone,
            )
        }

        assertEquals(unsupported, exception.failure)
        assertEquals(false, factoryInvoked)
    }

    @Test
    fun standaloneDirectFailureThrowsBeforeRunningApplication() {
        var applicationInvoked = false

        val exception = assertFailsWith<KadreException> {
            runKadreApplication(standalone) {
                applicationInvoked = true
            }
        }

        assertEquals(unsupported, exception.failure)
        assertEquals(false, applicationInvoked)
    }
}

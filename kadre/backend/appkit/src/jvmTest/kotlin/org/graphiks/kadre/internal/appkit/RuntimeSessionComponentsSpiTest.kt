package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kadre.internal.runtime.RuntimeHostController
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponents
import org.graphiks.kadre.internal.runtime.RuntimeSessionComponentsFactory
import org.graphiks.kadre.window.WindowManager
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeSessionComponentsSpiTest {
    @Test
    fun backendModuleCanConstructTheRuntimeSessionComponentsSpi() {
        val makeComponents: (WindowManager) -> RuntimeSessionComponents = { manager ->
            RuntimeSessionComponents(manager)
        }
        val factory = RuntimeSessionComponentsFactory { _, _ ->
            makeComponents(error("compile proof does not create components"))
        }

        val host = RuntimeHostController.withComponents(
            platform = KadrePlatform.AppKit,
            componentsFactory = factory,
        )

        assertEquals(KadrePlatform.AppKit, host.platform)
    }
}

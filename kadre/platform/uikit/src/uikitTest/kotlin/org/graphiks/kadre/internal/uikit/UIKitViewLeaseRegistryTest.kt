package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.surface.SurfaceId

/**
 * Le registre par surface du bail d'accès natif (Task 4) : un port enregistré est résolvable,
 * le retrait le rend introuvable et est IDEMPOTENT (miroir de la libération de
 * `UIKitSceneReservation`). Deux instances ne partagent rien — le registre est une CLASSE
 * instanciable (le `shared` n'est que le câblage de production), pas un singleton : l'isolement
 * par instance est ce qui rend le registre testable sans UIKit.
 */
class UIKitViewLeaseRegistryTest {
    /** Un port de test qui ne loue rien — l'identité (`assertSame`) est tout ce qui compte. */
    private fun stubPort(): UIKitViewLeasePort = object : UIKitViewLeasePort {
        override suspend fun <R> lease(block: suspend (Any) -> R): KadreResult<R> =
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.PlatformSurfaceAccess))
    }

    @Test
    fun registeredPortIsResolvableAndUnregisterRemovesIt() {
        val registry = UIKitViewLeaseRegistry()
        val id: SurfaceId = RuntimeProcessIds.nextSurfaceId()
        val port = stubPort()
        assertNull(registry.portFor(id))
        registry.register(id, port)
        assertSame(port, registry.portFor(id))
        registry.unregister(id)
        assertNull(registry.portFor(id))
        registry.unregister(id) // idempotent
    }

    @Test
    fun separateRegistryInstancesAreIsolated() {
        val registry = UIKitViewLeaseRegistry()
        val other = UIKitViewLeaseRegistry()
        val id: SurfaceId = RuntimeProcessIds.nextSurfaceId()
        val port = stubPort()
        registry.register(id, port)
        assertNull(other.portFor(id))
    }
}

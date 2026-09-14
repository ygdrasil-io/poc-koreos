package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.surface.SurfaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppKitCaptureSurfaceRegistryTest {
    @Test
    fun revokingOneSurfaceBlocksNewResolutionWithoutInvalidatingAnotherLiveSurface() {
        val registry = AppKitCaptureSurfaceRegistry()
        val first = surfaceId(1L)
        val second = surfaceId(2L)
        val firstRegistration = registry.register(first, 601L)
        val secondRegistration = registry.register(second, 602L)
        val heldFirstLease = registry.acquire(first)

        assertEquals(601L, heldFirstLease?.windowNumber)
        assertFailsWith<IllegalStateException> { registry.register(first, 999L) }
        assertEquals(601L, registry.acquire(first)?.windowNumber)

        firstRegistration.close()

        assertNull(registry.acquire(first))
        assertEquals(AppKitCaptureSurfaceResolution.Revoked, registry.resolve(first))
        assertEquals(AppKitCaptureSurfaceResolution.Unknown, registry.resolve(surfaceId(3L)))
        assertEquals(602L, registry.acquire(second)?.windowNumber)
        assertEquals(601L, heldFirstLease?.windowNumber)

        heldFirstLease?.close()
        secondRegistration.close()
    }
}

private fun surfaceId(value: Long): SurfaceId = SurfaceId::class.java
    .getDeclaredConstructor(Long::class.javaPrimitiveType)
    .apply { isAccessible = true }
    .newInstance(value)

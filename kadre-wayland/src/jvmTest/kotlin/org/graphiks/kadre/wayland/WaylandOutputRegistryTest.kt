package org.graphiks.kadre.wayland

import java.lang.foreign.Arena
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaylandOutputRegistryTest {

    @Test
    fun `all output globals are bound once and enumerated in registry order`() {
        val boundNames = mutableListOf<Pair<Int, Int>>()
        val listenerInstallations = mutableListOf<Long>()
        Arena.ofShared().use { arena ->
            val owner = registryOwner(
                arena = arena,
                bindOutput = { name, version ->
                    boundNames += name to version
                    (name * 100L) to version.coerceAtMost(4)
                },
                installOutputListener = {
                    listenerInstallations += it.proxy
                    true
                },
            )

            owner.onGlobal(name = 7, interfaceName = "wl_output", version = 2)
            owner.onGlobal(name = 11, interfaceName = "wl_output", version = 6)
            owner.onGlobal(name = 7, interfaceName = "wl_output", version = 2)

            assertEquals(listOf(7 to 2, 11 to 6), boundNames)
            assertEquals(listOf(7, 11), owner.outputs.map(BoundOutput::registryName))
            assertEquals(listOf(700L, 1_100L), owner.outputs.map(BoundOutput::proxy))
            assertEquals(listOf(2, 4), owner.outputs.map(BoundOutput::version))
            assertEquals(listOf(700L, 1_100L), listenerInstallations)
            owner.close()
        }
    }

    @Test
    fun `global remove destroys and removes only the matching output`() {
        val destroyed = mutableListOf<Long>()
        Arena.ofShared().use { arena ->
            val owner = registryOwner(
                arena = arena,
                bindOutput = { name, version -> (name * 100L) to version },
                destroyProxy = destroyed::add,
            )
            owner.onGlobal(name = 7, interfaceName = "wl_output", version = 2)
            owner.onGlobal(name = 11, interfaceName = "wl_output", version = 2)

            owner.onGlobalRemove(name = 11)

            assertEquals(listOf(1_100L), destroyed)
            assertEquals(listOf(7), owner.outputs.map(BoundOutput::registryName))
            assertNull(owner.outputForProxy(1_100L))
            assertEquals(700L, owner.outputForProxy(700L)?.proxy)
            owner.close()
        }
    }

    @Test
    fun `owner closes output children before registry and is idempotent`() {
        val destroyed = mutableListOf<Long>()
        var listenerArenaClosed = false
        val arena = Arena.ofShared()
        val owner = registryOwner(
            arena = arena,
            bindOutput = { name, version -> (name * 100L) to version },
            destroyProxy = destroyed::add,
            closeListenerArena = {
                listenerArenaClosed = true
                arena.close()
            },
        )
        owner.onGlobal(name = 7, interfaceName = "wl_output", version = 2)
        owner.onGlobal(name = 11, interfaceName = "wl_output", version = 2)
        owner.ownChild(AutoCloseable { destroyed += 8_000L })

        owner.close()
        owner.close()

        assertEquals(listOf(700L, 1_100L, 8_000L, 9_000L), destroyed)
        assertTrue(listenerArenaClosed)
        assertTrue(owner.outputs.isEmpty())
    }

    @Test
    fun `non output globals stay discoverable without being output-bound`() {
        Arena.ofShared().use { arena ->
            val owner = registryOwner(arena = arena)

            owner.onGlobal(name = 3, interfaceName = "wl_compositor", version = 6)

            assertTrue(owner.collector.hasProtocol("wl_compositor"))
            assertFalse(owner.collector.hasProtocol("wl_output"))
            assertTrue(owner.outputs.isEmpty())
            owner.close()
        }
    }

    @Test
    fun `failed output listener installation rolls binding back and reports failure`() {
        val destroyed = mutableListOf<Long>()
        Arena.ofShared().use { arena ->
            val owner = registryOwner(
                arena = arena,
                bindOutput = { name, version -> (name * 100L) to version },
                installOutputListener = { false },
                destroyProxy = destroyed::add,
            )

            owner.onGlobal(name = 7, interfaceName = "wl_output", version = 2)

            assertTrue(owner.outputs.isEmpty())
            assertEquals(listOf(700L), destroyed)
            assertFailsWith<IllegalStateException> { owner.throwPendingNativeFailure() }
            owner.close()
        }
    }

    @Test
    fun `null output bind is reported instead of silently omitting announced output`() {
        Arena.ofShared().use { arena ->
            val owner = registryOwner(
                arena = arena,
                bindOutput = { _, _ -> null },
            )

            owner.onGlobal(name = 7, interfaceName = "wl_output", version = 2)

            assertTrue(owner.outputs.isEmpty())
            val failure = assertFailsWith<IllegalStateException> {
                owner.throwPendingNativeFailure()
            }
            assertTrue(failure.message.orEmpty().contains("bind wl_output"))
            owner.close()
        }
    }

    @Test
    fun `throwing output listener installation is routed to configured native failure sink`() {
        val failures = mutableListOf<Throwable>()
        Arena.ofShared().use { arena ->
            val owner = registryOwner(
                arena = arena,
                bindOutput = { name, version -> (name * 100L) to version },
                installOutputListener = { error("listener boom") },
            )
            owner.routeNativeFailuresTo(failures::add)

            owner.onGlobal(name = 7, interfaceName = "wl_output", version = 2)

            assertTrue(owner.outputs.isEmpty())
            assertEquals("listener boom", failures.single().message)
            owner.close()
        }
    }

    @Test
    fun `Wayland globals protocol view stays live across future global and remove`() {
        Arena.ofShared().use { arena ->
            val owner = registryOwner(arena = arena)
            val globals = WaylandGlobals(
                compositorPtr = 0L,
                xdgWmBasePtr = 0L,
                registryOwner = owner,
            )

            owner.onGlobal(name = 3, interfaceName = "wl_compositor", version = 6)
            assertTrue(globals.hasProtocol("wl_compositor"))
            assertEquals(setOf("wl_compositor"), globals.availableProtocols)

            owner.onGlobalRemove(name = 3)
            assertFalse(globals.hasProtocol("wl_compositor"))
            assertTrue(globals.availableProtocols.isEmpty())
            owner.close()
        }
    }

    private fun registryOwner(
        arena: Arena,
        bindOutput: (Int, Int) -> Pair<Long, Int>? = { _, _ -> null },
        installOutputListener: (BoundOutput) -> Boolean = { true },
        destroyProxy: (Long) -> Unit = {},
        closeListenerArena: () -> Unit = {},
    ): WaylandRegistryOwner = WaylandRegistryOwner(
        registryPtr = 9_000L,
        listenerArena = arena,
        collector = GlobalsCollector(),
        bindOutput = bindOutput,
        installOutputListener = installOutputListener,
        destroyProxy = destroyProxy,
        closeListenerArena = closeListenerArena,
    )
}

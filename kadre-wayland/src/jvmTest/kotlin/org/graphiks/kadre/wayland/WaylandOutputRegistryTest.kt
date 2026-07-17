package org.graphiks.kadre.wayland

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WaylandOutputRegistryTest {

    @Test
    fun `geometry callback routes its exact failure to the native sink`() {
        assertOutputCallbackFailureIsRouted(
            customize = { expected ->
                copy(geometry = { _, _, _, _, _, _, _, _, _ -> throw expected })
            },
        ) { listener ->
            listener.onGeometry(
                MemorySegment.NULL, MemorySegment.NULL,
                0, 0, 0, 0, 0, MemorySegment.NULL, MemorySegment.NULL, 0,
            )
        }
    }

    @Test
    fun `mode callback routes its exact failure to the native sink`() {
        assertOutputCallbackFailureIsRouted(
            customize = { expected -> copy(mode = { _, _, _, _, _ -> throw expected }) },
        ) { listener ->
            listener.onMode(MemorySegment.NULL, MemorySegment.NULL, 0, 1920, 1080, 60_000)
        }
    }

    @Test
    fun `done callback routes its exact failure to the native sink`() {
        assertOutputCallbackFailureIsRouted(
            customize = { expected -> copy(done = { _ -> throw expected }) },
        ) { listener ->
            listener.onDone(MemorySegment.NULL, MemorySegment.NULL)
        }
    }

    @Test
    fun `scale callback routes its exact failure to the native sink`() {
        assertOutputCallbackFailureIsRouted(
            customize = { expected -> copy(scale = { _, _ -> throw expected }) },
        ) { listener ->
            listener.onScale(MemorySegment.NULL, MemorySegment.NULL, 2)
        }
    }

    @Test
    fun `name callback routes its exact failure to the native sink`() {
        assertOutputCallbackFailureIsRouted(
            customize = { expected -> copy(name = { _, _ -> throw expected }) },
        ) { listener ->
            listener.onName(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL)
        }
    }

    @Test
    fun `xdg pinger reports a missing pong operation without crossing the upcall`() {
        val failures = mutableListOf<Throwable>()
        val pinger = XdgWmBasePinger(
            pong = null,
            flush = { 0 },
            onFailure = failures::add,
        )

        pinger.onPing(MemorySegment.NULL, MemorySegment.NULL, 17)

        assertEquals(1, failures.size)
        assertTrue(failures.single().message.orEmpty().contains("pong"))
    }

    @Test
    fun `xdg pinger reports the exact native pong failure without crossing the upcall`() {
        val expected = IllegalStateException("pong boom")
        val failures = mutableListOf<Throwable>()
        val pinger = XdgWmBasePinger(
            pong = { throw expected },
            flush = { 0 },
            onFailure = failures::add,
        )

        pinger.onPing(MemorySegment.NULL, MemorySegment.NULL, 17)

        assertSame(expected, failures.single())
    }

    @Test
    fun `xdg pinger reports a negative display flush without crossing the upcall`() {
        val failures = mutableListOf<Throwable>()
        val pinger = XdgWmBasePinger(
            pong = {},
            flush = { -1 },
            onFailure = failures::add,
        )

        pinger.onPing(MemorySegment.NULL, MemorySegment.NULL, 17)

        assertEquals(1, failures.size)
        assertTrue(failures.single().message.orEmpty().contains("flush"))
    }

    @Test
    fun `xdg listener arena stays alive after destroy failure until display disconnect`() {
        val expected = IllegalStateException("destroy xdg")
        var listenerClosed = false
        val lifetime = WaylandNativeListenerLifetime()
        val lease = lifetime.register(AutoCloseable { listenerClosed = true })
        val binding = XdgWmBaseBinding(
            proxy = 88L,
            pinger = XdgWmBasePinger(pong = {}, flush = { 0 }, onFailure = {}),
            listenerLease = lease,
            destroyProxy = { throw expected },
        )

        val thrown = assertFailsWith<IllegalStateException> { binding.close() }
        assertSame(expected, thrown)
        assertFalse(listenerClosed)

        lifetime.closeAfterDisplayDisconnect()
        assertTrue(listenerClosed)
    }

    @Test
    fun `native failure replay retains pending work when the configured sink throws`() {
        val pending = IllegalStateException("pending")
        val sinkFailure = IllegalArgumentException("sink")
        Arena.ofShared().use { arena ->
            val owner = registryOwner(arena)
            owner.reportNativeFailure(pending)

            val thrown = assertFailsWith<IllegalArgumentException> {
                owner.routeNativeFailuresTo { throw sinkFailure }
            }
            assertSame(sinkFailure, thrown)

            val replayed = mutableListOf<Throwable>()
            owner.routeNativeFailuresTo(replayed::add)
            assertEquals(listOf<Throwable>(pending, sinkFailure), replayed)
            owner.close()
        }
    }

    @Test
    fun `native failure replay never self suppresses when sink throws the pending failure`() {
        val pending = IllegalStateException("pending")
        Arena.ofShared().use { arena ->
            val owner = registryOwner(arena)
            owner.reportNativeFailure(pending)

            val thrown = assertFailsWith<IllegalStateException> {
                owner.routeNativeFailuresTo { throw it }
            }
            assertSame(pending, thrown)

            val replayed = mutableListOf<Throwable>()
            owner.routeNativeFailuresTo(replayed::add)
            assertEquals(listOf<Throwable>(pending), replayed)
            assertTrue(pending.suppressed.isEmpty())
            owner.close()
        }
    }

    @Test
    fun `live sink failure disables sink and queues each later failure once`() {
        val first = IllegalStateException("first")
        val sinkFailure = IllegalArgumentException("sink")
        val second = IllegalStateException("second")
        var sinkCalls = 0
        Arena.ofShared().use { arena ->
            val owner = registryOwner(arena)
            owner.routeNativeFailuresTo {
                sinkCalls += 1
                throw sinkFailure
            }

            owner.reportNativeFailure(first)
            owner.reportNativeFailure(second)

            assertEquals(1, sinkCalls)
            val replayed = mutableListOf<Throwable>()
            owner.routeNativeFailuresTo(replayed::add)
            assertEquals(listOf<Throwable>(first, sinkFailure, second), replayed)
            owner.close()
        }
    }

    @Test
    fun `pending failure aggregation never self suppresses repeated throwable instance`() {
        val repeated = IllegalStateException("same")
        Arena.ofShared().use { arena ->
            val owner = registryOwner(arena)
            owner.reportNativeFailure(repeated)
            owner.reportNativeFailure(repeated)

            val thrown = assertFailsWith<IllegalStateException> {
                owner.throwPendingNativeFailure()
            }

            assertSame(repeated, thrown)
            assertTrue(repeated.suppressed.isEmpty())
            owner.close()
        }
    }

    @Test
    fun `registry constructor closes listener arena when lifetime registration fails`() {
        val lifetime = WaylandNativeListenerLifetime()
        lifetime.closeAfterDisplayDisconnect()
        var listenerClosed = false
        val arena = Arena.ofShared()

        assertFailsWith<IllegalStateException> {
            WaylandRegistryOwner(
                registryPtr = 9_000L,
                listenerArena = arena,
                collector = GlobalsCollector(),
                bindOutput = { _, _ -> null },
                installOutputListener = { null },
                destroyProxy = {},
                closeListenerArena = {
                    listenerClosed = true
                    arena.close()
                },
                nativeListenerLifetime = lifetime,
            )
        }

        assertTrue(listenerClosed)
    }

    @Test
    fun `discovery transaction rolls every acquired proxy back in reverse order at each stage`() {
        val stages = listOf(101L, 202L, 303L, 404L, 505L)

        stages.indices.forEach { failedStage ->
            val destroyed = mutableListOf<Long>()
            val transaction = WaylandProxyTransaction(destroyed::add)
            val expected = IllegalStateException("stage $failedStage")

            val thrown = assertFailsWith<IllegalStateException> {
                try {
                    stages.forEachIndexed { index, proxy ->
                        transaction.adopt(proxy)
                        if (index == failedStage) throw expected
                    }
                } catch (failure: Throwable) {
                    transaction.rollback(failure)
                    throw failure
                }
            }

            assertSame(expected, thrown)
            assertEquals(stages.take(failedStage + 1).asReversed(), destroyed)
        }
    }

    @Test
    fun `native listener installation preserves primary and suppresses cleanup failure`() {
        val primary = IllegalStateException("install")
        val cleanup = IllegalArgumentException("cleanup")
        val binding = AutoCloseable { throw cleanup }

        val thrown = assertFailsWith<IllegalStateException> {
            finalizeWaylandListenerInstallation(binding) { throw primary }
        }

        assertSame(primary, thrown)
        assertEquals(listOf(cleanup), thrown.suppressed.toList())
    }

    @Test
    fun `each hotplug output owns and releases one listener binding`() {
        val destroyed = mutableListOf<Long>()
        val listenerClosed = mutableListOf<Long>()
        Arena.ofShared().use { arena ->
            val owner = registryOwner(
                arena = arena,
                bindOutput = { name, version -> (name * 100L) to version },
                installOutputListener = { output ->
                    AutoCloseable { listenerClosed += output.proxy }
                },
                destroyProxy = destroyed::add,
            )

            repeat(3) {
                owner.onGlobal(name = 7, interfaceName = "wl_output", version = 4)
                owner.onGlobalRemove(name = 7)
            }

            assertEquals(listOf(700L, 700L, 700L), destroyed)
            assertEquals(destroyed, listenerClosed)
            assertTrue(owner.outputs.isEmpty())
            owner.close()
        }
    }

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
                    AutoCloseable {}
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
    fun `owner destroys adopted globals in reverse order before registry`() {
        val destroyed = mutableListOf<Long>()
        Arena.ofShared().use { arena ->
            val owner = registryOwner(
                arena = arena,
                destroyProxy = destroyed::add,
            )
            owner.ownGlobalProxy(100L)
            owner.ownGlobalProxy(200L)

            owner.close()

            assertEquals(listOf(200L, 100L, 9_000L), destroyed)
        }
    }

    @Test
    fun `registry listener arena stays alive after registry destroy failure until disconnect`() {
        val expected = IllegalStateException("destroy registry")
        var listenerClosed = false
        val lifetime = WaylandNativeListenerLifetime()
        val arena = Arena.ofShared()
        val owner = WaylandRegistryOwner(
            registryPtr = 9_000L,
            listenerArena = arena,
            collector = GlobalsCollector(),
            bindOutput = { _, _ -> null },
            installOutputListener = { null },
            destroyProxy = { throw expected },
            closeListenerArena = {
                listenerClosed = true
                arena.close()
            },
            nativeListenerLifetime = lifetime,
        )

        val thrown = assertFailsWith<IllegalStateException> { owner.close() }
        assertSame(expected, thrown)
        assertFalse(listenerClosed)

        lifetime.closeAfterDisplayDisconnect()
        assertTrue(listenerClosed)
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
                installOutputListener = { null },
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
        installOutputListener: (BoundOutput) -> AutoCloseable? = { AutoCloseable {} },
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

    private fun assertOutputCallbackFailureIsRouted(
        customize: WaylandOutputCallbackActions.(Throwable) -> WaylandOutputCallbackActions,
        invoke: (WlOutputListener) -> Unit,
    ) {
        val expected = IllegalStateException("callback boom")
        val failures = mutableListOf<Throwable>()
        val actions = WaylandOutputCallbackActions.default().customize(expected)
        val listener = WlOutputListener(
            info = WaylandOutputInfo(outputPtr = 700L, name = null, outputVersion = 4),
            onOutputChanged = null,
            onScaleChanged = null,
            onFailure = failures::add,
            actions = actions,
        )

        invoke(listener)

        assertSame(expected, failures.single())
    }
}

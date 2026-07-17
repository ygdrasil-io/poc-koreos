/**
 * Smoke tests for [WaylandWindow].
 *
 * These tests verify that [WaylandWindow] can be constructed with mock
 * pointers without causing a crash, and that the returned handles are correct.
 * They run on all platforms (macOS, Windows, Linux) without requiring
 * libwayland-client.so.0.
 */
package org.graphiks.kadre.wayland

import org.graphiks.kadre.ffi.wayland.*
import org.graphiks.kadre.core.CursorGrabMode
import org.graphiks.kadre.core.Icon
import org.graphiks.kadre.core.ImePurpose
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.Fullscreen
import org.graphiks.kadre.core.RawDisplayHandle
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.RequestError
import org.graphiks.kadre.core.SurfaceSizeRequestResult
import org.graphiks.kadre.core.Theme
import org.graphiks.kadre.core.UserAttentionType
import org.graphiks.kadre.core.WindowButtons
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.core.WindowRequestResult
import org.graphiks.kadre.core.WindowAttributes
import org.graphiks.kadre.core.WindowId
import org.graphiks.kadre.core.WindowLevel
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WaylandWindowTest {

    @Test
    fun `native close runs every child to parent cleanup and preserves failures`() {
        val trace = mutableListOf<String>()
        val primary = IllegalStateException("frame")
        val cursorFailure = IllegalArgumentException("cursor")
        val surfaceFailure = UnsupportedOperationException("surface")

        val thrown = assertFailsWith<IllegalStateException> {
            closeWaylandWindowResources(
                destroyFrameCallback = { trace += "frame"; throw primary },
                destroyCursor = { trace += "cursor"; throw cursorFailure },
                destroyBlur = { trace += "blur" },
                destroyXdgToplevel = { trace += "xdg-toplevel" },
                destroyXdgSurface = { trace += "xdg-surface" },
                destroyWlSurface = { trace += "wl-surface"; throw surfaceFailure },
            )
        }

        assertSame(primary, thrown)
        assertEquals(
            listOf("frame", "cursor", "blur", "xdg-toplevel", "xdg-surface", "wl-surface"),
            trace,
        )
        assertEquals(listOf(cursorFailure, surfaceFailure), thrown.suppressed.toList())
    }

    @Test
    fun `surface listener registration failure destroys proxy before closing binding`() {
        val trace = mutableListOf<String>()
        val lifetime = object : WaylandNativeListenerLifetime() {
            override fun register(binding: AutoCloseable): WaylandNativeListenerLease =
                error("registration failed")
        }

        val failure = assertFailsWith<IllegalStateException> {
            WaylandWindow.createForTest(
                surface = 42L,
                surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                    AutoCloseable { trace += "close-listener" }
                },
                surfaceProxyDestroyer = { trace += "destroy-$it" },
                nativeListenerLifetime = lifetime,
            )
        }

        assertEquals("registration failed", failure.message)
        assertEquals(listOf("destroy-42", "close-listener"), trace)
    }

    @Test
    fun `factory rollback never destroys surface twice after registration rollback`() {
        var destroyCalls = 0
        var bindingCloseCalls = 0
        val lifetime = object : WaylandNativeListenerLifetime() {
            override fun register(binding: AutoCloseable): WaylandNativeListenerLease =
                error("registration failed")
        }

        assertFailsWith<IllegalStateException> {
            withWaylandAcquiredSurfaceOwner(
                surfacePtr = 42L,
                destroySurface = { destroyCalls += 1 },
            ) { destroyOnce ->
                WaylandWindow.createForTest(
                    surface = 42L,
                    surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                        AutoCloseable { bindingCloseCalls += 1 }
                    },
                    surfaceProxyDestroyer = destroyOnce,
                    nativeListenerLifetime = lifetime,
                )
            }
        }

        assertEquals(1, destroyCalls)
        assertEquals(1, bindingCloseCalls)
    }

    @Test
    fun `surface listener registration defers binding when proxy destroy fails`() {
        val destroyFailure = IllegalArgumentException("destroy surface")
        var destroyCalls = 0
        var listenerClosed = false
        val lifetime = object : WaylandNativeListenerLifetime() {
            override fun register(binding: AutoCloseable): WaylandNativeListenerLease =
                error("registration failed")
        }

        val registration = assertFailsWith<IllegalStateException> {
            withWaylandAcquiredSurfaceOwner(
                surfacePtr = 42L,
                destroySurface = {
                    destroyCalls += 1
                    throw destroyFailure
                },
            ) { destroyOnce ->
                WaylandWindow.createForTest(
                    surface = 42L,
                    surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                        AutoCloseable { listenerClosed = true }
                    },
                    surfaceProxyDestroyer = destroyOnce,
                    nativeListenerLifetime = lifetime,
                )
            }
        }

        assertEquals(listOf(destroyFailure), registration.suppressed.toList())
        assertEquals(1, destroyCalls)
        assertFalse(listenerClosed)
        lifetime.closeAfterDisplayDisconnect()
        assertTrue(listenerClosed)
    }

    @Test
    fun `close destroys surface and listener exactly once`() {
        var destroyCalls = 0
        var listenerCloseCalls = 0
        Arena.ofShared().use { arena ->
            val registry = outputRegistry(arena)
            val window = WaylandWindow.createForTest(
                surface = 42L,
                surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                    AutoCloseable { listenerCloseCalls += 1 }
                },
                surfaceProxyDestroyer = { destroyCalls += 1 },
                surfaceFlusher = { 0 },
            ).also { it.registryOwner = registry }
            assertEquals(1, registry.outputRemovalListenerCount)
            assertEquals(1, registry.outputScaleListenerCount)

            window.close()
            window.close()

            assertEquals(1, destroyCalls)
            assertEquals(1, listenerCloseCalls)
            assertEquals(0, registry.outputRemovalListenerCount)
            assertEquals(0, registry.outputScaleListenerCount)
            registry.close()
        }
    }

    @Test
    fun `flush failure after destroy is terminal and retry never destroys twice`() {
        var destroyCalls = 0
        var listenerCloseCalls = 0
        val window = WaylandWindow.createForTest(
            surface = 42L,
            surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                AutoCloseable { listenerCloseCalls += 1 }
            },
            surfaceProxyDestroyer = { destroyCalls += 1 },
            surfaceFlusher = { -1 },
        )

        assertFailsWith<IllegalStateException> { window.close() }
        window.close()

        assertEquals(1, destroyCalls)
        assertEquals(1, listenerCloseCalls)
    }

    @Test
    fun `standalone factory exposes safe listener release after display disconnect`() {
        var listenerClosed = false
        val window = WaylandWindow.createForTest(
            surface = 42L,
            surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                AutoCloseable { listenerClosed = true }
            },
            surfaceProxyDestroyer = { error("destroy failed") },
        )

        assertFailsWith<IllegalStateException> { window.close() }
        assertFalse(listenerClosed)

        window.releaseNativeListenersAfterDisplayDisconnect()
        assertTrue(listenerClosed)
    }

    @Test
    fun `event loop window cannot release the shared connection listener lifetime`() {
        var connectionListenerClosed = false
        val sharedLifetime = WaylandNativeListenerLifetime()
        sharedLifetime.register(AutoCloseable { connectionListenerClosed = true })
        val window = WaylandWindow.createForTest(
            nativeListenerLifetime = sharedLifetime,
            ownsNativeListenerLifetime = false,
        )

        val failure = assertFailsWith<IllegalStateException> {
            window.releaseNativeListenersAfterDisplayDisconnect()
        }

        assertEquals(
            "Only a standalone WaylandWindow may release its native listener lifetime",
            failure.message,
        )
        assertFalse(connectionListenerClosed)
        sharedLifetime.closeAfterDisplayDisconnect()
        assertTrue(connectionListenerClosed)
    }

    @Test
    fun `entered output selection follows last enter then falls back on leave`() {
        Arena.ofShared().use { arena ->
            val registry = outputRegistry(arena)
            val outputA = registry.addTestOutput(name = 7, proxy = 700L, scale = 1)
            val outputB = registry.addTestOutput(name = 11, proxy = 1_100L, scale = 2)
            val window = WaylandWindow.createForTest().also { it.registryOwner = registry }

            window.onOutputEntered(outputA.proxy)
            window.onOutputEntered(outputB.proxy)
            assertEquals(outputB.proxy, window.currentMonitor()?.id)
            assertEquals(2.0, window.scaleFactor)

            window.onOutputLeft(outputB.proxy)
            assertEquals(outputA.proxy, window.currentMonitor()?.id)
            assertEquals(1.0, window.scaleFactor)

            window.onOutputLeft(outputA.proxy)
            assertNull(window.currentMonitor())
            assertEquals(1.0, window.scaleFactor)
            registry.close()
        }
    }

    @Test
    fun `hot unplug removes only that entered output and uses remaining output scale`() {
        Arena.ofShared().use { arena ->
            val registry = outputRegistry(arena)
            registry.addTestOutput(name = 7, proxy = 700L, scale = 1)
            registry.addTestOutput(name = 11, proxy = 1_100L, scale = 3)
            val window = WaylandWindow.createForTest().also { it.registryOwner = registry }
            window.onOutputEntered(700L)
            window.onOutputEntered(1_100L)

            registry.onGlobalRemove(11)

            assertEquals(700L, window.currentMonitor()?.id)
            assertEquals(1.0, window.scaleFactor)
            assertEquals(listOf(700L), window.enteredOutputProxies.toList())
            registry.close()
        }
    }

    @Test
    fun `surface listener installation routes enter and leave to window state`() {
        Arena.ofShared().use { arena ->
            val registry = outputRegistry(arena)
            registry.addTestOutput(name = 7, proxy = 700L, scale = 1)
            registry.addTestOutput(name = 11, proxy = 1_100L, scale = 2)
            var installedSurface = 0L
            lateinit var enter: (Long) -> Unit
            lateinit var leave: (Long) -> Unit
            val window = WaylandWindow.createForTest(
                surface = 42L,
                surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { surface, onEnter, onLeave, _ ->
                    installedSurface = surface
                    enter = onEnter
                    leave = onLeave
                    AutoCloseable {}
                },
            ).also { it.registryOwner = registry }

            assertEquals(42L, installedSurface)
            enter(700L)
            enter(1_100L)
            assertEquals(1_100L, window.currentMonitor()?.id)

            leave(1_100L)
            assertEquals(700L, window.currentMonitor()?.id)
            registry.close()
        }
    }

    @Test
    fun `surface listener owner closes when zero-pointer test window closes`() {
        var listenerClosed = false
        val window = WaylandWindow.createForTest(
            surface = 0L,
            surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                AutoCloseable { listenerClosed = true }
            },
        )

        window.close()

        assertTrue(listenerClosed)
    }

    @Test
    fun `surface listener stays alive when proxy destroy is unavailable until display disconnect`() {
        var listenerClosed = false
        val lifetime = WaylandNativeListenerLifetime()
        val window = WaylandWindow.createForTest(
            surface = 42L,
            surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                AutoCloseable { listenerClosed = true }
            },
            surfaceProxyDestroyer = null,
            nativeListenerLifetime = lifetime,
        )

        val failure = assertFailsWith<IllegalStateException> { window.close() }
        assertTrue(failure.message.orEmpty().contains("wl_proxy_destroy"))
        assertTrue(!listenerClosed)

        lifetime.closeAfterDisplayDisconnect()
        assertTrue(listenerClosed)
    }

    @Test
    fun `surface listener stays alive when proxy destroy throws until display disconnect`() {
        val expected = IllegalStateException("destroy surface")
        var listenerClosed = false
        val lifetime = WaylandNativeListenerLifetime()
        val window = WaylandWindow.createForTest(
            surface = 42L,
            surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ ->
                AutoCloseable { listenerClosed = true }
            },
            surfaceProxyDestroyer = { throw expected },
            nativeListenerLifetime = lifetime,
        )

        val thrown = assertFailsWith<IllegalStateException> { window.close() }
        assertSame(expected, thrown)
        assertTrue(!listenerClosed)

        lifetime.closeAfterDisplayDisconnect()
        assertTrue(listenerClosed)
    }

    @Test
    fun `real surface creation seam fails when surface output listener is not installed`() {
        val failure = assertFailsWith<IllegalStateException> {
            WaylandWindow.createForTest(
                surface = 42L,
                surfaceOutputListenerInstaller = WaylandSurfaceOutputListenerInstaller { _, _, _, _ -> null },
            )
        }

        assertTrue(failure.message.orEmpty().contains("wl_surface enter leave listener"))
    }

    @Test
    fun `surface construction failure destroys acquired proxy and preserves cause`() {
        val destroyed = mutableListOf<Long>()
        val failure = assertFailsWith<IllegalStateException> {
            withWaylandSurfaceRollback(
                surfacePtr = 42L,
                destroySurface = destroyed::add,
            ) {
                error("listener installation failed")
            }
        }

        assertEquals("listener installation failed", failure.message)
        assertEquals(listOf(42L), destroyed)
    }

    @Test
    fun `surface native listener routes enter callback failure without crossing upcall`() {
        val failures = mutableListOf<Throwable>()
        val listener = WlSurfaceOutputListener(
            onEnter = { error("surface enter boom") },
            onLeave = {},
            onFailure = failures::add,
        )

        listener.enter(
            data = MemorySegment.NULL,
            surface = MemorySegment.NULL,
            output = MemorySegment.ofAddress(700L),
        )

        assertEquals("surface enter boom", failures.single().message)
    }

    @Test
    fun `window enumerates every live registry output independently of entered set`() {
        Arena.ofShared().use { arena ->
            val registry = outputRegistry(arena)
            registry.addTestOutput(name = 7, proxy = 700L, scale = 1)
            registry.addTestOutput(name = 11, proxy = 1_100L, scale = 2)
            val window = WaylandWindow.createForTest().also { it.registryOwner = registry }
            window.onOutputEntered(1_100L)

            assertEquals(listOf(700L, 1_100L), window.availableMonitors().map { it.id })
            assertEquals(1_100L, window.currentMonitor()?.id)
            registry.close()
        }
    }

    @Test
    fun `selected window scale follows later scale events from that output`() {
        Arena.ofShared().use { arena ->
            val registry = outputRegistry(arena)
            val output = registry.addTestOutput(name = 11, proxy = 1_100L, scale = 2)
            val window = WaylandWindow.createForTest().also { it.registryOwner = registry }
            window.onOutputEntered(output.proxy)
            val events = mutableListOf<WindowEvent>()
            window.onWindowEvent = events::add

            output.info.scale = 3
            registry.notifyOutputScaleChanged(output.info, 3)

            assertEquals(3.0, window.scaleFactor)
            assertEquals(listOf<WindowEvent>(WindowEvent.ScaleFactorChanged(3.0)), events)
            registry.close()
        }
    }

    @Test
    fun `fullscreen output resolves explicit live monitor then current entered output`() {
        Arena.ofShared().use { arena ->
            val registry = outputRegistry(arena)
            val outputA = registry.addTestOutput(name = 7, proxy = 700L, scale = 1)
            val outputB = registry.addTestOutput(name = 11, proxy = 1_100L, scale = 2)
            val window = WaylandWindow.createForTest().also { it.registryOwner = registry }
            window.onOutputEntered(outputA.proxy)

            assertEquals(
                outputB.proxy,
                window.resolveFullscreenOutputProxy(Fullscreen.Borderless(outputB.info.toMonitorHandle())),
            )
            assertEquals(
                outputA.proxy,
                window.resolveFullscreenOutputProxy(Fullscreen.Borderless()),
            )
            registry.close()
        }
    }

    @Test
    fun `output video mode keeps refresh rate out of bit depth`() {
        val info = WaylandOutputInfo(outputPtr = 700L, name = "A", outputVersion = 4)

        info.updateMode(flags = 0x1, width = 1920, height = 1080, refresh = 60_000)

        val mode = info.toMonitorHandle().currentVideoMode
        assertNotNull(mode)
        assertEquals(PhysicalSize(1920, 1080), mode.size)
        assertNull(mode.bitDepth)
        assertEquals(60_000, mode.refreshRateMilliHz)
    }

    @Test
    fun `WaylandWindow can be constructed with mock pointers without crashing`() {
        // createForTest bypasses the FFM calls — works on all platforms
        val window = WaylandWindow.createForTest(
            display = 0L,
            compositor = 0L,
            xdgWmBase = 0L,
            surface = 0L,
        )
        assertNotNull(window)
    }

    @Test
    fun `rawWindowHandle returns Wayland type with correct pointers`() {
        val window = WaylandWindow.createForTest(
            display = 42L,
            compositor = 0L,
            surface = 99L,
        )
        val handle = window.rawWindowHandle
        assertIs<RawWindowHandle.Wayland>(handle)
        assertEquals(99L, handle.surface)
        assertEquals(42L, handle.display)
    }

    @Test
    fun `rawDisplayHandle returns Wayland type with correct display pointer`() {
        val window = WaylandWindow.createForTest(display = 123L)
        val handle = window.rawDisplayHandle
        assertIs<RawDisplayHandle.Wayland>(handle)
        assertEquals(123L, handle.display)
    }

    @Test
    fun `innerSize returns attrs size when provided`() {
        val attrs = WindowAttributes(size = PhysicalSize(1280, 720))
        val window = WaylandWindow.createForTest(attrs = attrs)
        assertEquals(PhysicalSize(1280, 720), window.innerSize)
    }

    @Test
    fun `innerSize returns default 800x600 when attrs size is null`() {
        val attrs = WindowAttributes(size = null)
        val window = WaylandWindow.createForTest(attrs = attrs)
        assertEquals(PhysicalSize(800, 600), window.innerSize)
    }

    @Test
    fun `outerSize equals innerSize`() {
        val window = WaylandWindow.createForTest()
        assertEquals(window.innerSize, window.outerSize)
    }

    @Test
    fun `scaleFactor is 1 0`() {
        val window = WaylandWindow.createForTest()
        assertEquals(1.0, window.scaleFactor)
    }

    @Test
    fun `onConfigure updates innerSize when dimensions are positive`() {
        val window = WaylandWindow.createForTest()
        window.onConfigure(1920, 1080)
        assertEquals(PhysicalSize(1920, 1080), window.innerSize)
    }

    @Test
    fun `requestSurfaceSize applies surface size immediately like winit Wayland`() {
        val window = WaylandWindow.createForTest()
        val events = mutableListOf<WindowEvent>()
        window.onWindowEvent = events::add

        val result = window.requestSurfaceSize(PhysicalSize(640, 480))

        assertIs<SurfaceSizeRequestResult.Applied>(result)
        assertEquals(PhysicalSize(640, 480), result.size)
        assertEquals(PhysicalSize(640, 480), window.surfaceSize)
        assertEquals(PhysicalSize(640, 480), window.innerSize)
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `requestSurfaceSize keeps compositor controlled size in non stateless Wayland states`() {
        val window = WaylandWindow.createForTest(attrs = WindowAttributes(size = PhysicalSize(800, 600)))
        val events = mutableListOf<WindowEvent>()
        window.onWindowEvent = events::add
        window.onToplevelStateConfigured(WaylandToplevelConfigureStates(fullscreen = true))

        val result = window.requestSurfaceSize(PhysicalSize(640, 480))

        assertIs<SurfaceSizeRequestResult.Applied>(result)
        assertEquals(PhysicalSize(800, 600), result.size)
        assertEquals(PhysicalSize(800, 600), window.surfaceSize)
        assertEquals(emptyList<WindowEvent>(), events)
    }

    @Test
    fun `Wayland configure states stateless matches winit request surface size rule`() {
        assertEquals(true, WaylandToplevelConfigureStates().isStateless())
        assertEquals(true, WaylandToplevelConfigureStates(resizing = true).isStateless())
        assertEquals(false, WaylandToplevelConfigureStates(maximized = true).isStateless())
        assertEquals(false, WaylandToplevelConfigureStates(fullscreen = true).isStateless())
        assertEquals(false, WaylandToplevelConfigureStates(tiled = true).isStateless())
    }

    @Test
    fun `Wayland min and max surface size constraints request redraw like winit`() {
        val window = WaylandWindow.createForTest()
        val events = mutableListOf<WindowEvent>()
        window.onWindowEvent = events::add

        window.setMinSurfaceSize(PhysicalSize(320, 200))
        window.setMaxSurfaceSize(PhysicalSize(1280, 720))

        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested, WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `Wayland setResizable requests redraw only when state changes like winit`() {
        val window = WaylandWindow.createForTest(attrs = WindowAttributes(resizable = true))
        val events = mutableListOf<WindowEvent>()
        window.onWindowEvent = events::add

        window.setResizable(true)
        assertEquals(emptyList<WindowEvent>(), events)

        window.setResizable(false)
        assertEquals(false, window.isResizable)
        assertEquals(listOf<WindowEvent>(WindowEvent.RedrawRequested), events)
    }

    @Test
    fun `surface resize increments are initialized from attrs and mutable`() {
        val window = WaylandWindow.createForTest(
            attrs = WindowAttributes(resizeIncrements = PhysicalSize(8, 16)),
        )
        assertEquals(PhysicalSize(8, 16), window.surfaceResizeIncrements)

        window.setSurfaceResizeIncrements(PhysicalSize(4, 6))
        assertEquals(PhysicalSize(4, 6), window.surfaceResizeIncrements)

        window.setSurfaceResizeIncrements(null)
        assertEquals(null, window.surfaceResizeIncrements)
    }

    @Test
    fun `transparent hint is initialized from attrs and mutable`() {
        val window = WaylandWindow.createForTest(
            attrs = WindowAttributes(transparent = true),
        )
        assertEquals(true, window.transparentHint)

        window.setTransparent(false)
        assertEquals(false, window.transparentHint)

        window.setTransparent(true)
        assertEquals(true, window.transparentHint)
    }

    @Test
    fun `Wayland theme is initialized from attrs and mutable like winit`() {
        val window = WaylandWindow.createForTest(
            attrs = WindowAttributes(preferredTheme = Theme.Dark),
        )
        assertEquals(Theme.Dark, window.theme)

        window.setTheme(Theme.Light)
        assertEquals(Theme.Light, window.theme)

        window.setTheme(null)
        assertEquals(null, window.theme)
    }

    @Test
    fun `Wayland content protection is a success no-op like winit`() {
        assertEquals(WindowRequestResult.Success, waylandContentProtectionResult(true))
        assertEquals(WindowRequestResult.Success, waylandContentProtectionResult(false))
    }

    @Test
    fun `Wayland enabled buttons are all after set like winit`() {
        assertEquals(WindowButtons.ALL, waylandEnabledButtons())
        assertEquals(WindowButtons.ALL, waylandEnabledButtonsAfterSet(WindowButtons.NONE))
        assertEquals(WindowButtons.ALL, waylandEnabledButtonsAfterSet(WindowButtons.CLOSE))
        assertEquals(WindowButtons.ALL, waylandEnabledButtonsAfterSet(WindowButtons.ALL))
    }

    @Test
    fun `Wayland window level is a safe no-op like winit`() {
        val window = WaylandWindow.createForTest(surface = 4_242L)

        window.setWindowLevel(WindowLevel.Normal)
        window.setWindowLevel(WindowLevel.AlwaysOnTop)
        window.setWindowLevel(WindowLevel.AlwaysOnBottom)
    }

    @Test
    fun `Wayland opaque region uses full positive extent like winit`() {
        assertEquals(Int.MAX_VALUE, WAYLAND_OPAQUE_REGION_EXTENT)
    }

    @Test
    fun `Wayland cursor hittest disabled uses empty input region like winit`() {
        assertEquals(WaylandRegionRect(x = 0, y = 0, width = 0, height = 0), waylandEmptyInputRegionRect())
    }

    @Test
    fun `Wayland cursor hittest reports unsupported without a surface`() {
        val window = WaylandWindow.createForTest(surface = 0L)

        val result = window.setCursorHittest(false)

        assertIs<WindowRequestResult.Failure>(result)
        assertIs<RequestError.Unsupported>(result.error)
    }

    @Test
    fun `Wayland cursor grab none is a success no-op like winit`() {
        val window = WaylandWindow.createForTest()

        assertEquals(WindowRequestResult.Success, window.setCursorGrab(CursorGrabMode.None))

        val confined = window.setCursorGrab(CursorGrabMode.Confined)
        assertIs<WindowRequestResult.Failure>(confined)
        assertIs<RequestError.Unsupported>(confined.error)
        assertEquals("Wayland pointer constraints are not available", confined.error.message)
    }

    @Test
    fun `Wayland cursor grab with zero pointerConstraintsPtr still succeeds for None`() {
        val window = WaylandWindow.createForTest(pointerConstraintsPtr = 0L)

        // None should succeed
        assertEquals(WindowRequestResult.Success, window.setCursorGrab(CursorGrabMode.None))

        // Confined should fail since constraintsPtr is 0
        val confined = window.setCursorGrab(CursorGrabMode.Confined)
        assertIs<WindowRequestResult.Failure>(confined)
        assertIs<RequestError.Unsupported>(confined.error)
    }

    @Test
    fun `WaylandPointerConstraints grab with zero constraintsPtr returns Unsupported`() {
        val pc = WaylandPointerConstraints(0L)
        // No FFM calls needed — the code path checks constraintsPtr first
        val locked = pc.grab(surfacePtr = 1L, pointerPtr = 2L, mode = CursorGrabMode.Locked)
        assertIs<WindowRequestResult.Failure>(locked)
        assertIs<RequestError.Unsupported>(locked.error)

        val confined = pc.grab(surfacePtr = 1L, pointerPtr = 2L, mode = CursorGrabMode.Confined)
        assertIs<WindowRequestResult.Failure>(confined)
        assertIs<RequestError.Unsupported>(confined.error)

        val none = pc.grab(surfacePtr = 1L, pointerPtr = 2L, mode = CursorGrabMode.None)
        assertIs<WindowRequestResult.Success>(none)
    }

    @Test
    fun `WaylandPointerConstraints release does not crash without FFM bindings`() {
        val pc = WaylandPointerConstraints(0L)
        pc.release() // Must not throw
        pc.grab(surfacePtr = 0L, pointerPtr = 0L, mode = CursorGrabMode.None)
        pc.release() // Must not throw (idempotent)
    }

    @Test
    fun `Wayland cursor visibility state is mutable and hide is best effort`() {
        val surface = 4_242L
        val window = WaylandWindow.createForTest(surface = surface)

        window.setCursorVisible(false)
        assertEquals(false, window.cursorVisible)
        assertEquals(false, WaylandPointerState.isCursorVisible(surface))

        window.setCursorVisible(true)
        assertEquals(true, window.cursorVisible)
        assertEquals(true, WaylandPointerState.isCursorVisible(surface))

        WaylandPointerState.leaveSurface(surface)
    }

    @Test
    fun `Wayland pointer state exposes cursor request context from enter serial`() {
        val surface = 6_363L
        WaylandPointerState.enterPointer(ptr = 7_474L, surfacePtr = surface, serial = 11)

        val context = WaylandPointerState.currentCursor(surface)

        assertNotNull(context)
        assertEquals(7_474L, context.pointerPtr)
        assertEquals(11, context.enterSerial)

        WaylandPointerState.leaveSurface(surface)
        assertEquals(null, WaylandPointerState.currentCursor(surface))
    }

    @Test
    fun `Wayland decoration mode follows winit decorated flag`() {
        assertEquals(XDG_TOPLEVEL_DECORATION_MODE_SERVER_SIDE, waylandDecorationMode(decorated = true))
        assertEquals(XDG_TOPLEVEL_DECORATION_MODE_CLIENT_SIDE, waylandDecorationMode(decorated = false))
    }

    @Test
    fun `Wayland decorations state is initialized from attrs and mutable`() {
        val window = WaylandWindow.createForTest(
            attrs = WindowAttributes(decorations = false),
        )
        assertEquals(false, window.isDecorated)

        window.setDecorations(true)
        assertEquals(true, window.isDecorated)

        window.setDecorations(false)
        assertEquals(false, window.isDecorated)
    }

    @Test
    fun `onConfigure applies resize increments from minimum size base`() {
        val attrs = WindowAttributes(
            minSize = PhysicalSize(100, 50),
            resizeIncrements = PhysicalSize(30, 20),
        )
        val window = WaylandWindow.createForTest(attrs = attrs)

        window.onConfigure(176, 99)

        assertEquals(PhysicalSize(160, 90), window.innerSize)
    }

    @Test
    fun `onConfigure can skip resize increments for compositor enforced sizes`() {
        val attrs = WindowAttributes(
            minSize = PhysicalSize(100, 50),
            resizeIncrements = PhysicalSize(30, 20),
        )
        val window = WaylandWindow.createForTest(attrs = attrs)

        window.onConfigure(176, 99, applyResizeIncrements = false)

        assertEquals(PhysicalSize(176, 99), window.innerSize)
    }

    @Test
    fun `Wayland toplevel configure states update maximized and fullscreen like winit`() {
        val window = WaylandWindow.createForTest()

        window.onToplevelStateConfigured(
            WaylandToplevelConfigureStates(maximized = true, fullscreen = true),
        )

        assertEquals(true, window.isMaximized)
        assertIs<Fullscreen.Borderless>(window.fullscreen)

        window.onToplevelStateConfigured(WaylandToplevelConfigureStates())

        assertEquals(false, window.isMaximized)
        assertEquals(null, window.fullscreen)
    }

    @Test
    fun `Wayland hasFocus follows keyboard seat focus like winit`() {
        val surface = 8_484L
        val window = WaylandWindow.createForTest(surface = surface)
        WaylandFocusState.clear(surface)

        assertEquals(false, window.hasFocus)
        assertEquals(true, WaylandFocusState.addSeatFocus(surface, seatPtr = 1L))
        assertEquals(true, window.hasFocus)
        assertEquals(false, WaylandFocusState.addSeatFocus(surface, seatPtr = 2L))
        assertEquals(true, window.hasFocus)
        assertEquals(false, WaylandFocusState.removeSeatFocus(surface, seatPtr = 1L))
        assertEquals(true, window.hasFocus)
        assertEquals(true, WaylandFocusState.removeSeatFocus(surface, seatPtr = 2L))
        assertEquals(false, window.hasFocus)
    }

    @Test
    fun `Wayland focus removal ignores unknown surfaces`() {
        val surface = 9_595L
        WaylandFocusState.clear(surface)

        assertEquals(false, WaylandFocusState.removeSeatFocus(surface, seatPtr = 1L))
        assertEquals(false, WaylandFocusState.hasFocus(surface))
    }

    @Test
    fun `Wayland fullscreen and maximized setters wait for compositor configure like winit`() {
        val window = WaylandWindow.createForTest()

        window.setMaximized(true)
        window.setFullscreen(Fullscreen.Borderless())

        assertEquals(false, window.isMaximized)
        assertEquals(null, window.fullscreen)
    }

    @Test
    fun `Wayland resize increments apply only for resizing unconstrained states`() {
        assertEquals(
            true,
            waylandShouldApplyResizeIncrements(
                isResizing = true,
                isMaximized = false,
                isFullscreen = false,
                isTiled = false,
            ),
        )
        assertEquals(
            false,
            waylandShouldApplyResizeIncrements(
                isResizing = true,
                isMaximized = true,
                isFullscreen = false,
                isTiled = false,
            ),
        )
        assertEquals(
            false,
            waylandShouldApplyResizeIncrements(
                isResizing = false,
                isMaximized = false,
                isFullscreen = false,
                isTiled = false,
            ),
        )
    }

    @Test
    fun `wayland resize increments ignore invalid increments`() {
        val size = PhysicalSize(176, 99)
        assertEquals(size, waylandApplyResizeIncrements(size, PhysicalSize(100, 50), PhysicalSize(0, 20)))
        assertEquals(size, waylandApplyResizeIncrements(size, PhysicalSize(100, 50), PhysicalSize(30, -1)))
        assertEquals(size, waylandApplyResizeIncrements(size, PhysicalSize(100, 50), null))
    }

    @Test
    fun `onConfigure ignores zero dimensions`() {
        val attrs = WindowAttributes(size = PhysicalSize(800, 600))
        val window = WaylandWindow.createForTest(attrs = attrs)
        window.onConfigure(0, 0)
        assertEquals(PhysicalSize(800, 600), window.innerSize)
    }

    @Test
    fun `id is based on surface pointer`() {
        val window = WaylandWindow.createForTest(surface = 7777L)
        assertEquals(7777L, window.id.value)
    }

    @Test
    fun `close does not crash with null surface`() {
        // surface = 0 → close() must return without an FFM call
        val window = WaylandWindow.createForTest(surface = 0L)
        window.close() // Must not throw an exception
    }

    @Test
    fun `requestRedraw does not crash with null surface`() {
        val window = WaylandWindow.createForTest(surface = 0L)
        window.requestRedraw() // Must not throw an exception
    }

    @Test
    fun `bare redraw commit is forbidden while xdg shell acquisition is in progress`() {
        assertTrue(shouldCommitBareWaylandSurface(surfacePtr = 42L, xdgWmBasePtr = 0L))
        assertFalse(shouldCommitBareWaylandSurface(surfacePtr = 0L, xdgWmBasePtr = 0L))
        assertFalse(shouldCommitBareWaylandSurface(surfacePtr = 42L, xdgWmBasePtr = 99L))
    }

    @Test
    fun `setTitle does not crash`() {
        val window = WaylandWindow.createForTest()
        window.setTitle("Test Window") // Stub — must not throw an exception
    }

    @Test
    fun `setVisible is ignored on Wayland without native calls like winit`() {
        val window = WaylandWindow.createForTest(surface = 4_242L)
        window.setVisible(true)
        window.setVisible(false)
    }

    @Test
    fun `Wayland setWindowIcon is a silent no-op when iconManager is unavailable`() {
        val window = WaylandWindow.createForTest(iconManagerPtr = 0L)
        // Must not throw — protocol extension is optional
        window.setWindowIcon(null)
        window.setWindowIcon(Icon(ByteArray(16), 2, 2))
    }

    @Test
    fun `Wayland setWindowIcon is a silent no-op without xdg toplevel`() {
        val window = WaylandWindow.createForTest(iconManagerPtr = 1L)
        // xdgToplevelPtr returns 0 when xdg is null → no-op
        window.setWindowIcon(null)
        window.setWindowIcon(Icon(ByteArray(16), 2, 2))
    }

    @Test
    fun `WaylandIconManager creation with zero iconManagerPtr produces null manager`() {
        val window = WaylandWindow.createForTest(iconManagerPtr = 0L)
        assertNull(window.iconManager)
    }

    @Test
    fun `WaylandIconManager creation with valid iconManagerPtr produces non-null manager`() {
        val shm = 42L
        val window = WaylandWindow.createForTest(
            iconManagerPtr = 100L,
            shmPtr = shm,
        )
        assertNotNull(window.iconManager)
    }

    @Test
    fun `WaylandIconManager createShmBuffer returns 0 when shmPtr is 0`() {
        val buf = WaylandIconManager.createShmBuffer(0L, 16, 16, ByteArray(256))
        assertEquals(0L, buf)
    }

    @Test
    fun `WaylandIconManager createShmBuffer returns 0 with invalid dimensions`() {
        assertEquals(0L, WaylandIconManager.createShmBuffer(1L, 0, 16, ByteArray(64)))
        assertEquals(0L, WaylandIconManager.createShmBuffer(1L, 16, 0, ByteArray(64)))
        assertEquals(0L, WaylandIconManager.createShmBuffer(1L, -1, 16, ByteArray(64)))
    }

    @Test
    fun `Wayland setBlur is a silent no-op when blurManager is unavailable`() {
        val window = WaylandWindow.createForTest(extBackgroundEffectManagerPtr = 0L, kwinBlurManagerPtr = 0L)
        assertNull(window.blurManager)
        // Must not throw — protocol extension is optional
        window.setBlur(true)
        window.setBlur(false)
    }

    @Test
    fun `Wayland blurManager is non-null when ext background effect ptr is non-zero`() {
        val window = WaylandWindow.createForTest(
            surface = 1L,
            extBackgroundEffectManagerPtr = 100L,
        )
        assertNotNull(window.blurManager)
    }

    @Test
    fun `Wayland blurManager is non-null when kwin blur ptr is non-zero`() {
        val window = WaylandWindow.createForTest(
            surface = 1L,
            kwinBlurManagerPtr = 200L,
        )
        assertNotNull(window.blurManager)
    }

    @Test
    fun `Wayland blurManager setBlur does not crash with mock pointers`() {
        if (libWaylandClient != null) return // Skip on Wayland Linux — requires real proxies
        val window = WaylandWindow.createForTest(
            surface = 1L,
            extBackgroundEffectManagerPtr = 100L,
        )
        val blur = window.blurManager
        assertNotNull(blur)
        blur.setBlur(true)
        blur.setBlur(false)
        blur.setBlur(true)
    }

    @Test
    fun `Wayland blurManager kwin fallback setBlur does not crash with mock pointers`() {
        if (libWaylandClient != null) return
        val window = WaylandWindow.createForTest(
            surface = 1L,
            kwinBlurManagerPtr = 200L,
        )
        val blur = window.blurManager
        assertNotNull(blur)
        blur.setBlur(true)
        blur.setBlur(false)
    }

    @Test
    fun `Wayland blurManager with zero surface is a no-op`() {
        val blur = WaylandBlur(extBackgroundEffectManagerPtr = 100L, kwinBlurManagerPtr = 0L, surfacePtr = 0L)
        // Must not throw
        blur.setBlur(true)
        blur.setBlur(false)
    }

    @Test
    fun `WaylandActivationToken is a silent no-op when activationManagerPtr is zero`() {
        val act = WaylandActivationToken(activationManagerPtr = 0L)
        // Must not throw — protocol extension is optional
        act.activate("test-token", surfacePtr = 100L)
    }

    @Test
    fun `WaylandActivationToken activate does not crash with mock pointers`() {
        if (libWaylandClient != null) return
        val act = WaylandActivationToken(activationManagerPtr = 200L, seatPtr = 300L)
        act.activate("test-token", surfacePtr = 100L)
    }

    @Test
    fun `WaylandActivationToken activate with zero seat is a no-op`() {
        if (libWaylandClient != null) return
        val act = WaylandActivationToken(activationManagerPtr = 200L, seatPtr = 0L)
        act.activate("test-token", surfacePtr = 100L)
    }

    @Test
    fun `WaylandActivationToken activate with zero surface is a no-op`() {
        val act = WaylandActivationToken(activationManagerPtr = 200L, seatPtr = 300L)
        act.activate("test-token", surfacePtr = 0L)
    }

    @Test
    fun `WaylandWindow createForTest accepts activationManagerPtr`() {
        val window = WaylandWindow.createForTest(
            activationManagerPtr = 100L,
            seatPtr = 200L,
        )
        assertNotNull(window)
    }

    @Test
    fun `WaylandWindow setActivationToken is a silent no-op when activationManager is unavailable`() {
        val window = WaylandWindow.createForTest(activationManagerPtr = 0L)
        window.setActivationToken("test-token")
    }

    @Test
    fun `WaylandWindow setActivationToken with null token is a silent no-op`() {
        val window = WaylandWindow.createForTest(activationManagerPtr = 100L, seatPtr = 200L)
        window.setActivationToken(null)
    }

    @Test
    fun `WaylandWindow create returns null when libwayland is not available`() {
        // On macOS/Windows, wlCompositorCreateSurface is null
        // → create() returns null gracefully
        if (libWaylandClient != null) return // Skip on Wayland Linux

        val result = WaylandWindow.create(
            display = 0L,
            compositor = 0L,
            xdgWmBase = 0L,
            shmPtr = 0L,
            attrs = WindowAttributes(),
        )
        // On non-Wayland, the binding is null and create() returns null
        assertEquals(null, result)
    }

    @Test
    fun `setAppId does not crash when xdg is unavailable`() {
        val window = WaylandWindow.createForTest()
        // xdg is null → setAppId is a silent no-op
        window.setAppId("org.example.app")
    }

    @Test
    fun `setAppId does not crash with blank app ID`() {
        val window = WaylandWindow.createForTest()
        window.setAppId("")
    }

    // ── R5-requestUserAttention ──────────────────────────────────────────────

    @Test
    fun `WaylandActivationToken requestAttention returns false when activationManagerPtr is zero`() {
        val act = WaylandActivationToken(activationManagerPtr = 0L)
        assertEquals(false, act.requestAttention(surfacePtr = 100L, seatPtr = 200L, serial = 42))
    }

    @Test
    fun `WaylandActivationToken requestAttention returns false with mock pointers`() {
        if (libWaylandClient != null) return
        val act = WaylandActivationToken(activationManagerPtr = 200L, seatPtr = 300L)
        // FFI handles are null on non-Wayland → returns false without crashing
        assertEquals(false, act.requestAttention(surfacePtr = 100L, seatPtr = 200L, serial = 42))
    }

    @Test
    fun `WaylandActivationToken requestAttention returns false with zero surface`() {
        val act = WaylandActivationToken(activationManagerPtr = 200L, seatPtr = 300L)
        assertEquals(false, act.requestAttention(surfacePtr = 0L, seatPtr = 200L, serial = 42))
    }

    @Test
    fun `WaylandWindow requestUserAttention with null requestType returns Success`() {
        val window = WaylandWindow.createForTest()
        val result = window.requestUserAttention(null)
        assertIs<WindowRequestResult.Success>(result)
    }

    @Test
    fun `WaylandWindow requestUserAttention returns Unsupported when activationManager is null`() {
        val window = WaylandWindow.createForTest(activationManagerPtr = 0L)
        val result = window.requestUserAttention(UserAttentionType.Informational)
        assertIs<WindowRequestResult.Failure>(result)
        assertIs<RequestError.Unsupported>(result.error)
        assertEquals("Wayland xdg_activation_v1 is unavailable", result.error.message)
    }

    @Test
    fun `WaylandWindow requestUserAttention returns Ignored when token flow fails`() {
        if (libWaylandClient != null) return
        val surface = 4_242L
        val window = WaylandWindow.createForTest(
            activationManagerPtr = 100L,
            seatPtr = 200L,
            surface = surface,
        )
        val result = window.requestUserAttention(UserAttentionType.Informational)
        assertIs<WindowRequestResult.Failure>(result)
        assertIs<RequestError.Ignored>(result.error)
    }

    @Test
    fun `WaylandWindow requestUserAttention uses PointerState seat info`() {
        if (libWaylandClient != null) return
        val surface = 7_373L
        WaylandPointerState.enterPointer(ptr = 3_333L, surfacePtr = surface, serial = 77)
        WaylandPointerState.recordButton(serial = 88, button = 1, state = 1)
        val window = WaylandWindow.createForTest(
            activationManagerPtr = 100L,
            seatPtr = 200L,
            surface = surface,
        )
        val result = window.requestUserAttention(UserAttentionType.Informational)
        // Should return Ignored (token flow fails with mock ptrs) not Unsupported
        assertIs<WindowRequestResult.Failure>(result)
        assertIs<RequestError.Ignored>(result.error)
        WaylandPointerState.leaveSurface(surface)
    }

    // ── R5-IME: ──────────────────────────────────────────────────────────────

    @Test
    fun `setImeAllowed does not crash with mock pointers`() {
        if (libWaylandClient != null) return
        val window = WaylandWindow.createForTest()
        window.setImeAllowed(true)
        window.setImeAllowed(false)
    }

    @Test
    fun `setImeCursorArea does not crash with mock pointers`() {
        if (libWaylandClient != null) return
        val window = WaylandWindow.createForTest()
        window.setImeCursorArea(PhysicalPosition(10, 20), PhysicalSize(100, 30))
    }

    @Test
    fun `setImePurpose does not crash with mock pointers`() {
        if (libWaylandClient != null) return
        val window = WaylandWindow.createForTest()
        window.setImePurpose(ImePurpose.Normal)
        window.setImePurpose(ImePurpose.Password)
        window.setImePurpose(ImePurpose.Terminal)
    }

    @Test
    fun `ImeEvent Enabled and Disabled are object singletons`() {
        assertSame(WindowEvent.Ime.ImeEvent.Enabled, WindowEvent.Ime.ImeEvent.Enabled)
        assertSame(WindowEvent.Ime.ImeEvent.Disabled, WindowEvent.Ime.ImeEvent.Disabled)
    }

    @Test
    fun `ImeEvent Preedit stores text and cursor range`() {
        val preedit = WindowEvent.Ime.ImeEvent.Preedit("hello", Pair(0, 5))
        assertEquals("hello", preedit.text)
        assertEquals(Pair(0, 5), preedit.cursorRange)

        val emptyRange = WindowEvent.Ime.ImeEvent.Preedit("", null)
        assertEquals("", emptyRange.text)
        assertNull(emptyRange.cursorRange)
    }

    @Test
    fun `ImeEvent Commit stores text`() {
        val commit = WindowEvent.Ime.ImeEvent.Commit("world")
        assertEquals("world", commit.text)
    }

    @Test
    fun `ImeEvent DeleteSurrounding stores before and after byte counts`() {
        val del = WindowEvent.Ime.ImeEvent.DeleteSurrounding(3, 5)
        assertEquals(3, del.beforeBytes)
        assertEquals(5, del.afterBytes)
    }

    @Test
    fun `WindowEvent Ime wraps any ImeEvent variant`() {
        val enabled = WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Enabled)
        assertIs<WindowEvent.Ime>(enabled)
        assertIs<WindowEvent.Ime.ImeEvent.Enabled>(enabled.ime)

        val commit = WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Commit("test"))
        assertIs<WindowEvent.Ime>(commit)
        assertIs<WindowEvent.Ime.ImeEvent.Commit>(commit.ime)
        assertEquals("test", (commit.ime as WindowEvent.Ime.ImeEvent.Commit).text)
    }

    @Test
    fun `routeWaylandInputEvent routes Ime events with all variants`() {
        val surface = 42L
        val window = WaylandWindow.createForTest(surface = surface)
        val queue = ConcurrentLinkedQueue<Pair<WindowId, WindowEvent>>()
        val windows = mapOf(surface to window)

        val variants = listOf(
            WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Enabled),
            WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Preedit("abc", Pair(0, 3))),
            WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Commit("hello")),
            WindowEvent.Ime(WindowEvent.Ime.ImeEvent.DeleteSurrounding(2, 4)),
            WindowEvent.Ime(WindowEvent.Ime.ImeEvent.Disabled),
        )

        for (event in variants) {
            assertTrue(routeWaylandInputEvent(surface, event, windows, queue))
            val (wid, queued) = queue.poll() ?: error("expected queued event")
            assertEquals(window.id, wid)
            assertEquals(event, queued)
        }
        assertNull(queue.poll())
    }

    private fun outputRegistry(arena: Arena): WaylandRegistryOwner = WaylandRegistryOwner(
        registryPtr = 9_000L,
        listenerArena = arena,
        collector = GlobalsCollector(),
        bindOutput = { name, version -> (name * 100L) to version.coerceAtMost(4) },
        installOutputListener = { AutoCloseable {} },
        destroyProxy = {},
        closeListenerArena = {},
    )

    private fun WaylandRegistryOwner.addTestOutput(
        name: Int,
        proxy: Long,
        scale: Int,
    ): BoundOutput {
        onGlobal(name = name, interfaceName = "wl_output", version = 4)
        return checkNotNull(outputForProxy(proxy)).also { it.info.scale = scale }
    }
}

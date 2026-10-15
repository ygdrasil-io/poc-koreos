package org.graphiks.kadre.internal.wayland

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.DisplayPortSnapshot
import org.graphiks.kadre.internal.runtime.SurfaceRedrawCommand
import org.graphiks.kadre.internal.runtime.SurfaceRedrawGeneration
import org.graphiks.kadre.internal.wayland.assembleDisplaySnapshot
import org.graphiks.kadre.surface.SurfaceId
import org.graphiks.kadre.window.WindowId
import org.graphiks.kffi.wayland.managed.WaylandOutputMode
import org.graphiks.kffi.wayland.managed.WaylandOutputState

/**
 * Preuves O2 phase 3 : assemblage du snapshot display (logique P-1, F1-1,
 * F1-3), coalescence du redraw sur le pacing frame (F2-2) et refus après
 * disparition (SN-1). Les preuves O3 dynamiques (swaymsg scale, retrait)
 * couvrent P-2/P-3 côté compositor réel.
 */
class WaylandMetricsO2Test {

    private fun state(
        scale: Int = 1,
        width: Int = 640,
        height: Int = 480,
        name: String? = "HEADLESS-1",
    ): WaylandOutputState = WaylandOutputState(
        geometryX = 0, geometryY = 0,
        physicalWidthMm = 320, physicalHeightMm = 240,
        subpixel = 0, transform = 0,
        scale = scale, name = name, description = null,
        modes = listOf(WaylandOutputMode(flags = 3, width = width, height = height, refreshMillihertz = 60000)),
        currentModeIndex = 0,
    )

    @Test
    fun snapshotAssemblesFromCachedOutputs() {
        val snapshot = assembleDisplaySnapshot(
            listOf(WaylandOutputSnapshot(key = 15L, state = state())),
            primaryKey = 15L,
        )
        assertIs<DisplayPortSnapshot>(snapshot)
        assertEquals(1, snapshot.displays.size)
        assertEquals(15L, snapshot.primaryKey)
        val display = snapshot.displays.single()
        assertEquals(640, display.bounds.size.width)
        assertEquals(1.0, display.scaleFactor)
        // mode key = (global name shl 8) or mode index = (15 shl 8) or 0
        assertEquals(3840L, display.currentModeKey)
        assertNull(display.workArea, "aucune source protocolaire pour la workArea")
    }

    @Test
    fun unusableOutputsAreExcludedNotZeroScaled() {
        // F1-3 : un output sans mode courant est exclu, jamais publié à scale 0
        val snapshot = assembleDisplaySnapshot(
            listOf(
                WaylandOutputSnapshot(key = 1L, state = state().copy(currentModeIndex = -1)),
                WaylandOutputSnapshot(key = 2L, state = state(scale = 2, name = "TWO")),
            ),
            primaryKey = null,
        )
        assertIs<DisplayPortSnapshot>(snapshot)
        assertEquals(1, snapshot.displays.size)
        assertEquals(2.0, snapshot.displays.single().scaleFactor)
    }

    @Test
    fun outputRemovalWithdrawsItWithoutSubstitutingThePrimary() {
        // F1-1 : après retrait, le snapshot suivant ne contient plus la display
        // et le primaire retiré n'est jamais substitué
        val before = assembleDisplaySnapshot(
            listOf(WaylandOutputSnapshot(15L, state()), WaylandOutputSnapshot(16L, state(name = "TWO"))),
            primaryKey = 15L,
        )
        assertIs<DisplayPortSnapshot>(before)
        assertEquals(2, before.displays.size)
        val after = assembleDisplaySnapshot(
            listOf(WaylandOutputSnapshot(16L, state(name = "TWO"))),
            primaryKey = 15L,
        )
        assertIs<DisplayPortSnapshot>(after)
        assertEquals(1, after.displays.size)
        assertNull(after.primaryKey)
    }

    @Test
    fun redrawRequestsCoalesceIntoOneFrameCommit() {
        // F2-2 : N demandes pendant un cycle frame ⇒ exactement un commit
        val window = FakeNativeWindow()
        val port = WaylandSurfaceCommandPort()
        val surfaceId = SurfaceId(1L)
        val peer = WaylandWindowPeer(
            windowId = WindowId(1L),
            window = window,
            onNativeClosed = {},
        )
        port.register(surfaceId, peer)

        val first = port.requestRedraw(SurfaceRedrawCommand(surfaceId, SurfaceRedrawGeneration.fromNative(1L)))
        val second = port.requestRedraw(SurfaceRedrawCommand(surfaceId, SurfaceRedrawGeneration.fromNative(2L)))
        assertIs<KadreResult.Success<Unit>>(first)
        assertIs<KadreResult.Success<Unit>>(second)
        assertEquals(0, window.commitCurrentCount.get(), "rien ne part avant le frame callback")

        window.deliverFrame()
        assertEquals(1, window.commitCurrentCount.get(), "le burst coalesce en un seul commit")

        // une demande après le frame réarme proprement un cycle
        port.requestRedraw(SurfaceRedrawCommand(surfaceId, SurfaceRedrawGeneration.fromNative(3L)))
        window.deliverFrame()
        assertEquals(2, window.commitCurrentCount.get())
    }

    @Test
    fun redrawAfterSurfaceDisappearanceIsRejected() {
        // SN-1 : la demande pour une surface disparue est refusée
        val port = WaylandSurfaceCommandPort()
        val outcome = port.requestRedraw(SurfaceRedrawCommand(SurfaceId(99L), SurfaceRedrawGeneration.fromNative(1L)))
        assertEquals(KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Surface)), outcome)
    }
}

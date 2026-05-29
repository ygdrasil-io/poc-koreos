/**
 * Tests unitaires pour KoreosWndProc.
 *
 * Vérifie que les messages Win32 (WM_*) sont correctement traduits en [WindowEvent]
 * koreos et transmis au handler installé.
 *
 * Ces tests s'exécutent sur toutes les plateformes (macOS, Linux, Windows) car
 * le dispatch est une logique pure Kotlin. Seul defWindowProcW retourne 0 sur
 * macOS/Linux (MethodHandle FFM null) — comportement documenté et attendu.
 *
 * Note : [KoreosWndProc] est un singleton. Chaque test doit installer son propre
 * handler et le désinstaller en fin de test pour éviter les interférences.
 */
package io.ygdrasil.koreos.win32

import io.ygdrasil.koreos.core.Key
import io.ygdrasil.koreos.core.KeyState
import io.ygdrasil.koreos.core.MouseButton
import io.ygdrasil.koreos.core.PhysicalSize
import io.ygdrasil.koreos.core.WindowEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Fake HWND pour les tests (valeur arbitraire non nulle). */
private const val TEST_HWND: Long = 0x1234_5678L

class KoreosWndProcTest {

    /** Dernier événement capturé par le handler de test. */
    private var capturedHwnd: Long? = null
    private var capturedEvent: WindowEvent? = null

    @BeforeTest
    fun setup() {
        capturedHwnd = null
        capturedEvent = null
        KoreosWndProc.install { hwnd, event ->
            capturedHwnd = hwnd
            capturedEvent = event
        }
    }

    @AfterTest
    fun teardown() {
        KoreosWndProc.uninstall()
    }

    // ── WM_PAINT ──────────────────────────────────────────────────────────────

    @Test
    fun `WM_PAINT emet RedrawRequested`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_PAINT, 0L, 0L)

        assertEquals(TEST_HWND, capturedHwnd)
        assertIs<WindowEvent.RedrawRequested>(capturedEvent)
    }

    @Test
    fun `WM_PAINT retourne 0`() {
        val result = KoreosWndProc.dispatch(TEST_HWND, WM_PAINT, 0L, 0L)
        assertEquals(0L, result)
    }

    // ── WM_SIZE ───────────────────────────────────────────────────────────────

    @Test
    fun `WM_SIZE emet Resized avec dimensions correctes`() {
        // lParam = MAKELPARAM(800, 600) = 600 << 16 | 800
        val lParam = (600L shl 16) or 800L
        KoreosWndProc.dispatch(TEST_HWND, WM_SIZE, 0L, lParam)

        assertIs<WindowEvent.Resized>(capturedEvent).also { event ->
            assertEquals(PhysicalSize(800, 600), event.size)
        }
    }

    @Test
    fun `WM_SIZE retourne 0`() {
        val result = KoreosWndProc.dispatch(TEST_HWND, WM_SIZE, 0L, 0L)
        assertEquals(0L, result)
    }

    // ── WM_KEYDOWN ────────────────────────────────────────────────────────────

    @Test
    fun `WM_KEYDOWN emet KeyboardInput Pressed pour touche A`() {
        // wParam = VK_A, lParam = 0 (pas de répétition)
        KoreosWndProc.dispatch(TEST_HWND, WM_KEYDOWN, VK_A.toLong(), 0L)

        assertIs<WindowEvent.KeyboardInput>(capturedEvent).also { event ->
            assertEquals(Key.A, event.key)
            assertEquals(KeyState.Pressed, event.state)
            assertEquals(false, event.isRepeat)
        }
    }

    @Test
    fun `WM_KEYDOWN detecte la repetition via bit 30 de lParam`() {
        // Bit 30 = 0x4000_0000 → touche déjà enfoncée (répétition)
        val lParamRepeat = KF_REPEAT
        KoreosWndProc.dispatch(TEST_HWND, WM_KEYDOWN, VK_SPACE.toLong(), lParamRepeat)

        assertIs<WindowEvent.KeyboardInput>(capturedEvent).also { event ->
            assertEquals(Key.Space, event.key)
            assertEquals(KeyState.Pressed, event.state)
            assertEquals(true, event.isRepeat)
        }
    }

    @Test
    fun `WM_KEYUP emet KeyboardInput Released`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_KEYUP, VK_ESCAPE.toLong(), 0L)

        assertIs<WindowEvent.KeyboardInput>(capturedEvent).also { event ->
            assertEquals(Key.Escape, event.key)
            assertEquals(KeyState.Released, event.state)
            assertEquals(false, event.isRepeat)
        }
    }

    @Test
    fun `WM_SYSKEYDOWN emet KeyboardInput Pressed pour touche F4`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_SYSKEYDOWN, VK_F4.toLong(), 0L)

        assertIs<WindowEvent.KeyboardInput>(capturedEvent).also { event ->
            assertEquals(Key.F4, event.key)
            assertEquals(KeyState.Pressed, event.state)
        }
    }

    @Test
    fun `WM_SYSKEYUP emet KeyboardInput Released`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_SYSKEYUP, VK_F4.toLong(), 0L)

        assertIs<WindowEvent.KeyboardInput>(capturedEvent).also { event ->
            assertEquals(Key.F4, event.key)
            assertEquals(KeyState.Released, event.state)
        }
    }

    // ── WM_MOUSEMOVE ──────────────────────────────────────────────────────────

    @Test
    fun `WM_MOUSEMOVE emet PointerMoved avec coordonnees correctes`() {
        // lParam = MAKELPARAM(320, 240)
        val lParam = (240L shl 16) or 320L
        KoreosWndProc.dispatch(TEST_HWND, WM_MOUSEMOVE, 0L, lParam)

        assertIs<WindowEvent.PointerMoved>(capturedEvent).also { event ->
            assertEquals(320.0, event.position.x)
            assertEquals(240.0, event.position.y)
        }
    }

    // ── Boutons de souris ─────────────────────────────────────────────────────

    @Test
    fun `WM_LBUTTONDOWN emet MouseInput Left Pressed`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_LBUTTONDOWN, 0L, 0L)
        assertIs<WindowEvent.MouseInput>(capturedEvent).also { event ->
            assertEquals(MouseButton.Left, event.button)
            assertEquals(KeyState.Pressed, event.state)
        }
    }

    @Test
    fun `WM_LBUTTONUP emet MouseInput Left Released`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_LBUTTONUP, 0L, 0L)
        assertIs<WindowEvent.MouseInput>(capturedEvent).also { event ->
            assertEquals(MouseButton.Left, event.button)
            assertEquals(KeyState.Released, event.state)
        }
    }

    @Test
    fun `WM_RBUTTONDOWN emet MouseInput Right Pressed`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_RBUTTONDOWN, 0L, 0L)
        assertIs<WindowEvent.MouseInput>(capturedEvent).also { event ->
            assertEquals(MouseButton.Right, event.button)
            assertEquals(KeyState.Pressed, event.state)
        }
    }

    @Test
    fun `WM_RBUTTONUP emet MouseInput Right Released`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_RBUTTONUP, 0L, 0L)
        assertIs<WindowEvent.MouseInput>(capturedEvent).also { event ->
            assertEquals(MouseButton.Right, event.button)
            assertEquals(KeyState.Released, event.state)
        }
    }

    @Test
    fun `WM_MBUTTONDOWN emet MouseInput Middle Pressed`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_MBUTTONDOWN, 0L, 0L)
        assertIs<WindowEvent.MouseInput>(capturedEvent).also { event ->
            assertEquals(MouseButton.Middle, event.button)
            assertEquals(KeyState.Pressed, event.state)
        }
    }

    @Test
    fun `WM_MBUTTONUP emet MouseInput Middle Released`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_MBUTTONUP, 0L, 0L)
        assertIs<WindowEvent.MouseInput>(capturedEvent).also { event ->
            assertEquals(MouseButton.Middle, event.button)
            assertEquals(KeyState.Released, event.state)
        }
    }

    // ── WM_MOUSEWHEEL ─────────────────────────────────────────────────────────

    @Test
    fun `WM_MOUSEWHEEL emet MouseWheel avec delta positif vers le haut`() {
        // wParam HIWORD = +120 (un cran vers le haut)
        val wParam = 120L shl 16
        KoreosWndProc.dispatch(TEST_HWND, WM_MOUSEWHEEL, wParam, 0L)

        assertIs<WindowEvent.MouseWheel>(capturedEvent).also { event ->
            assertEquals(0.0, event.deltaX)
            assertEquals(1.0, event.deltaY)
        }
    }

    @Test
    fun `WM_MOUSEWHEEL emet MouseWheel avec delta negatif vers le bas`() {
        // wParam HIWORD = -120 (un cran vers le bas) → SHORT(-120) = 0xFF88
        val shortNeg120 = (-120).toShort().toInt() and 0xFFFF
        val wParam = (shortNeg120.toLong() shl 16)
        KoreosWndProc.dispatch(TEST_HWND, WM_MOUSEWHEEL, wParam, 0L)

        assertIs<WindowEvent.MouseWheel>(capturedEvent).also { event ->
            assertEquals(0.0, event.deltaX)
            assertEquals(-1.0, event.deltaY)
        }
    }

    // ── WM_CLOSE ──────────────────────────────────────────────────────────────

    @Test
    fun `WM_CLOSE emet CloseRequested`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_CLOSE, 0L, 0L)
        assertIs<WindowEvent.CloseRequested>(capturedEvent)
    }

    @Test
    fun `WM_CLOSE retourne 0 sans appeler DefWindowProcW`() {
        val result = KoreosWndProc.dispatch(TEST_HWND, WM_CLOSE, 0L, 0L)
        assertEquals(0L, result)
    }

    // ── WM_DESTROY ────────────────────────────────────────────────────────────

    @Test
    fun `WM_DESTROY emet Destroyed`() {
        KoreosWndProc.dispatch(TEST_HWND, WM_DESTROY, 0L, 0L)
        assertIs<WindowEvent.Destroyed>(capturedEvent)
    }

    @Test
    fun `WM_DESTROY retourne 0`() {
        val result = KoreosWndProc.dispatch(TEST_HWND, WM_DESTROY, 0L, 0L)
        assertEquals(0L, result)
    }

    // ── WM_DPICHANGED ─────────────────────────────────────────────────────────

    @Test
    fun `WM_DPICHANGED emet ScaleFactorChanged avec facteur correct`() {
        // wParam LOWORD = 192 DPI → facteur 2.0 (192 / 96)
        val wParam = 192L
        KoreosWndProc.dispatch(TEST_HWND, WM_DPICHANGED, wParam, 0L)

        assertIs<WindowEvent.ScaleFactorChanged>(capturedEvent).also { event ->
            assertEquals(2.0, event.factor)
        }
    }

    @Test
    fun `WM_DPICHANGED avec 96 DPI retourne facteur 1 0`() {
        // wParam LOWORD = 96 DPI → facteur 1.0 (identité)
        val wParam = 96L
        KoreosWndProc.dispatch(TEST_HWND, WM_DPICHANGED, wParam, 0L)

        assertIs<WindowEvent.ScaleFactorChanged>(capturedEvent).also { event ->
            assertEquals(1.0, event.factor)
        }
    }

    // ── Message inconnu ───────────────────────────────────────────────────────

    @Test
    fun `message inconnu ne transmet pas d evenement au handler`() {
        // 0xDEAD n'est pas un WM_* connu — doit aller dans else → defWindowProcW
        // Sur macOS/Linux, defWindowProcW est null → retourne 0
        KoreosWndProc.dispatch(TEST_HWND, 0xDEAD, 0L, 0L)
        // Aucun événement ne doit avoir été émis
        assertNull(capturedEvent)
    }

    // ── Handler non installé ──────────────────────────────────────────────────

    @Test
    fun `dispatch sans handler installe ne leve pas d exception`() {
        KoreosWndProc.uninstall()
        // Ne doit pas lever d'exception — les événements sont silencieusement ignorés
        KoreosWndProc.dispatch(TEST_HWND, WM_PAINT, 0L, 0L)
        KoreosWndProc.dispatch(TEST_HWND, WM_CLOSE, 0L, 0L)
    }

    // ── Constantes Win32 ──────────────────────────────────────────────────────

    @Test
    fun `constantes WM ont les valeurs hexadecimales attendues`() {
        assertEquals(0x000F, WM_PAINT)
        assertEquals(0x0005, WM_SIZE)
        assertEquals(0x0100, WM_KEYDOWN)
        assertEquals(0x0101, WM_KEYUP)
        assertEquals(0x0104, WM_SYSKEYDOWN)
        assertEquals(0x0105, WM_SYSKEYUP)
        assertEquals(0x0200, WM_MOUSEMOVE)
        assertEquals(0x0201, WM_LBUTTONDOWN)
        assertEquals(0x0202, WM_LBUTTONUP)
        assertEquals(0x0204, WM_RBUTTONDOWN)
        assertEquals(0x0205, WM_RBUTTONUP)
        assertEquals(0x0207, WM_MBUTTONDOWN)
        assertEquals(0x0208, WM_MBUTTONUP)
        assertEquals(0x020A, WM_MOUSEWHEEL)
        assertEquals(0x0010, WM_CLOSE)
        assertEquals(0x0002, WM_DESTROY)
        assertEquals(0x02E0, WM_DPICHANGED)
    }

    @Test
    fun `KF_REPEAT est le bit 30`() {
        assertEquals(0x4000_0000L, KF_REPEAT)
    }
}

/**
 * WaylandBlur — blur background effect support for Kadre Wayland backend.
 *
 * Supports two optional Wayland protocols:
 *  1. `ext_background_effect_v1` — wlroots protocol (used by Sway, River, KWin 6+)
 *  2. `org_kde_kwin_blur_manager` — KDE KWin 5.x protocol
 *
 * Tries `ext_background_effect_v1` first (modern), falls back to
 * `org_kde_kwin_blur` if unavailable. Silent no-op if neither is available.
 *
 * ### KWin integration (#270)
 * - KWin 5.x: uses `org_kde_kwin_blur_manager` — `set_region` with NULL = full-surface blur.
 * - KWin 6.x: uses `ext_background_effect_v1` — `set_background_effects` with bitmask 0/1.
 * - Both require a `wl_surface.commit` for the effect to take effect.
 * - Error handling: protocol errors are caught silently; the compositor
 *   may disconnect the client on invalid protocol usage.
 */
package org.graphiks.kadre.wayland
import org.graphiks.kadre.ffi.wayland.*

import java.lang.foreign.MemorySegment

/**
 * Identifies which KWin blur protocol variant is in use.
 */
internal enum class KwinBlurVariant {
    /** ext_background_effect_v1 (KWin 6+, wlroots). */
    ExtBackgroundEffect,
    /** org_kde_kwin_blur_manager (KWin 5.x). */
    KwinBlurManager,
    /** No blur protocol available. */
    None,
}

/**
 * Manages blur background effect for a single [WaylandWindow].
 *
 * The constructor takes pointers to the two possible blur protocol managers.
 * At most one should be non-zero (whichever the compositor exposes), but
 * [setBlur] tries `ext_background_effect_v1` first regardless.
 *
 * @param extBackgroundEffectManagerPtr  ext_background_effect_v1* or 0.
 * @param kwinBlurManagerPtr             org_kde_kwin_blur_manager* or 0.
 * @param surfacePtr                     wl_surface* this window owns.
 */
internal class WaylandBlur(
    private val extBackgroundEffectManagerPtr: Long,
    private val kwinBlurManagerPtr: Long,
    private val surfacePtr: Long,
) {
    /** ext_background_effect_surface_v1 proxy, or 0 if not created. */
    private var effectSurfacePtr: Long = 0L

    /** org_kde_kwin_blur proxy, or 0 if not created. */
    private var kwinBlurPtr: Long = 0L

    /**
     * The detected KWin blur variant.
     */
    val variant: KwinBlurVariant get() = when {
        extBackgroundEffectManagerPtr != 0L -> KwinBlurVariant.ExtBackgroundEffect
        kwinBlurManagerPtr != 0L -> KwinBlurVariant.KwinBlurManager
        else -> KwinBlurVariant.None
    }

    /**
     * Returns true if the compositor supports any blur protocol.
     */
    val isSupported: Boolean get() = variant != KwinBlurVariant.None

    /**
     * Returns true if KWin 6+ (ext_background_effect_v1) is in use.
     */
    val isKwin6: Boolean get() = variant == KwinBlurVariant.ExtBackgroundEffect

    /**
     * Returns true if KWin 5.x (org_kde_kwin_blur_manager) is in use.
     */
    val isKwin5: Boolean get() = variant == KwinBlurVariant.KwinBlurManager

    /**
     * Enables or disables background blur on this surface.
     *
     * After setting the blur state, calls [wlSurfaceCommit] to make the
     * effect take effect (required by both KWin 5.x and KWin 6+).
     *
     * @param blur `true` to enable blur, `false` to disable.
     */
    fun setBlur(blur: Boolean) {
        if (surfacePtr == 0L) return
        val hadExt = extBackgroundEffectManagerPtr != 0L
        val hadKwin = kwinBlurManagerPtr != 0L
        if (hadExt) {
            setBlurExtBackgroundEffect(blur)
        } else if (hadKwin) {
            setBlurKwin(blur)
        }
        // KWin requires wl_surface.commit for the blur effect to take effect.
        // Only commit when a blur protocol is actually available.
        if (hadExt || hadKwin) {
            wlSurfaceCommit(surfacePtr)
        }
    }

    /**
     * Destroys all blur proxy objects. Called on window shutdown.
     */
    fun destroy() {
        if (effectSurfacePtr != 0L) {
            destroyProxy(effectSurfacePtr)
            effectSurfacePtr = 0L
        }
        if (kwinBlurPtr != 0L) {
            destroyProxy(kwinBlurPtr)
            kwinBlurPtr = 0L
        }
    }

    private fun setBlurExtBackgroundEffect(blur: Boolean) {
        val create = extBackgroundEffectV1Create ?: return

        if (blur && effectSurfacePtr == 0L) {
            effectSurfacePtr = try {
                (create.invokeExact(
                    MemorySegment.ofAddress(extBackgroundEffectManagerPtr),
                    1, // ext_background_effect_v1.create opcode
                    extBackgroundEffectSurfaceV1Interface,
                    1, // version
                    0, // flags
                    MemorySegment.ofAddress(surfacePtr),
                    MemorySegment.NULL,
                ) as MemorySegment).address()
            } catch (_: Throwable) {
                0L
            }
        }

        if (effectSurfacePtr != 0L) {
            val setEffects = wlProxyMarshalFlagsUint ?: return
            try {
                setEffects.invokeExact(
                    MemorySegment.ofAddress(effectSurfacePtr),
                    1, // ext_background_effect_surface_v1.set_background_effects opcode
                    MemorySegment.NULL,
                    1, // version
                    0, // flags
                    if (blur) 1 else 0,
                )
            } catch (_: Throwable) { }
        }

        if (!blur && effectSurfacePtr != 0L) {
            effectSurfacePtr = destroyProxy(effectSurfacePtr)
        }
    }

    private fun setBlurKwin(blur: Boolean) {
        val create = kwinBlurManagerCreate ?: return

        if (blur && kwinBlurPtr == 0L) {
            kwinBlurPtr = try {
                (create.invokeExact(
                    MemorySegment.ofAddress(kwinBlurManagerPtr),
                    1, // org_kde_kwin_blur_manager.create opcode
                    orgKdeKwinBlurInterface,
                    1, // version
                    0, // flags
                    MemorySegment.ofAddress(surfacePtr),
                    MemorySegment.NULL,
                ) as MemorySegment).address()
            } catch (_: Throwable) {
                0L
            }

            if (kwinBlurPtr != 0L) {
                setKwinBlurRegion()
            }
        }

        if (!blur && kwinBlurPtr != 0L) {
            kwinBlurPtr = destroyProxy(kwinBlurPtr)
        }
    }

    private fun setKwinBlurRegion() {
        val setRegion = wlProxyMarshalFlagsObject ?: return
        try {
            setRegion.invokeExact(
                MemorySegment.ofAddress(kwinBlurPtr),
                1, // org_kde_kwin_blur.set_region opcode
                MemorySegment.NULL,
                1, // version
                0, // flags
                MemorySegment.NULL, // NULL = full-surface blur effect
            )
        } catch (_: Throwable) { }
    }

    /**
     * Destroys a Wayland proxy object via opcode 0 (destroy).
     * Returns 0 to clear the reference in the caller.
     */
    private fun destroyProxy(proxyPtr: Long): Long {
        val destroy = wlProxyMarshalFlagsVoid ?: return 0L
        try {
            destroy.invokeExact(
                MemorySegment.ofAddress(proxyPtr),
                0, // destroy opcode
                MemorySegment.NULL,
                1, // version
                0, // flags
            )
        } catch (_: Throwable) { }
        return 0L
    }
}

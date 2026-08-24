/**
 * Linux/Wayland OpenGL context via EGL, bound to a Kadre Wayland window (wl_display* + wl_surface*).
 *
 * Wraps the wl_surface in a wl_egl_window, creates a desktop-GL EGL context with an RGBA8 +
 * stencil config and presents with eglSwapBuffers. Works with Mesa software GL (llvmpipe)
 * under a headless Weston compositor for CI.
 *
 * Wayland has no buffer-size query, so the size is tracked from Kadre resize events via [resize].
 */
package org.graphiks.kadre.samples.compose.infra

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.GLAssembledInterface
import org.jetbrains.skia.makeGLWithInterface

private const val EGL_OPENGL_API = 0x30A2
private const val EGL_NONE = 0x3038
private const val EGL_CONTEXT_MAJOR_VERSION_KHR = 0x3098
private const val EGL_CONTEXT_MINOR_VERSION_KHR = 0x30FB
private const val EGL_CONTEXT_OPENGL_PROFILE_MASK_KHR = 0x30FD
private const val EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT_KHR = 0x0001
private const val EGL_VENDOR = 0x3053
private const val EGL_VERSION = 0x3054
private const val EGL_CLIENT_APIS = 0x308D
private const val GL_VENDOR = 0x1F00
private const val GL_RENDERER = 0x1F01
private const val GL_VERSION = 0x1F02

class WaylandEglContext(displayAddr: Long, surfaceAddr: Long) : GlContext {

    private val wlDisplay = NativeFfi.ptr(displayAddr)
    private val wlSurface = NativeFfi.ptr(surfaceAddr)
    private val arena = Arena.ofShared()

    private var eglDisplay = MemorySegment.NULL
    private var eglContext = MemorySegment.NULL
    private var eglSurface = MemorySegment.NULL
    private var eglWindow = MemorySegment.NULL
    private var skiaGlInterface: GLAssembledInterface? = null
    private var created = false
    private var diagnosticsReported = false

    private var width = 1
    private var height = 1

    override fun makeCurrent() {
        if (!created) create()
        val madeCurrent = eglMakeCurrent.call(eglDisplay, eglSurface, eglSurface, eglContext) as Int
        check(madeCurrent != 0) {
            "eglMakeCurrent failed (EGL error 0x${(eglGetError.call() as Int).toString(16)})"
        }
        reportDriverOnce()
        // Disable vsync throttling. The default swap interval is 1, so eglSwapBuffers blocks on
        // the compositor's frame callback — which never fires until the surface is mapped and
        // presented. For the synchronous headless capture path that deadlocks, so present
        // immediately. (Requires a current context, hence after eglMakeCurrent.)
        eglSwapInterval.call(eglDisplay, 0)
    }

    private fun create() {
        eglDisplay = eglGetDisplay.call(wlDisplay) as MemorySegment
        check(eglDisplay != MemorySegment.NULL) { "eglGetDisplay returned EGL_NO_DISPLAY" }
        check((eglInitialize.call(eglDisplay, MemorySegment.NULL, MemorySegment.NULL) as Int) != 0) {
            "eglInitialize failed"
        }
        check((eglBindAPI.call(EGL_OPENGL_API) as Int) != 0) { "eglBindAPI(EGL_OPENGL_API) failed" }

        val configAttrs = intArrayOf(
            0x3033, 0x0004, // EGL_SURFACE_TYPE, EGL_WINDOW_BIT
            0x3040, 0x0008, // EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT
            0x3024, 8,      // EGL_RED_SIZE
            0x3023, 8,      // EGL_GREEN_SIZE
            0x3022, 8,      // EGL_BLUE_SIZE
            0x3021, 8,      // EGL_ALPHA_SIZE
            0x3025, 24,     // EGL_DEPTH_SIZE
            0x3026, 8,      // EGL_STENCIL_SIZE
            EGL_NONE,
        )
        val attrSeg = intSegment(configAttrs)
        val configs = arena.allocate(ADDRESS.byteSize())
        val numConfig = arena.allocate(JAVA_INT.byteSize())
        check((eglChooseConfig.call(eglDisplay, attrSeg, configs, 1, numConfig) as Int) != 0) {
            "eglChooseConfig failed"
        }
        check(numConfig.get(JAVA_INT, 0L) > 0) { "eglChooseConfig returned no configs" }
        val config = configs.get(ADDRESS, 0L)

        eglWindow = wlEglWindowCreate.call(wlSurface, width, height) as MemorySegment
        check(eglWindow != MemorySegment.NULL) { "wl_egl_window_create failed" }

        eglSurface = eglCreateWindowSurface.call(eglDisplay, config, eglWindow, MemorySegment.NULL) as MemorySegment
        check(eglSurface != MemorySegment.NULL) { "eglCreateWindowSurface failed" }

        // EGL's default OpenGL context can be the legacy 1.0 profile, which lacks the
        // capabilities Skia needs for DirectContext.makeGL. Request the desktop core
        // profile explicitly; Mesa llvmpipe provides it on the headless CI compositor.
        val ctxAttrs = intSegment(intArrayOf(
            EGL_CONTEXT_MAJOR_VERSION_KHR, 3,
            EGL_CONTEXT_MINOR_VERSION_KHR, 3,
            EGL_CONTEXT_OPENGL_PROFILE_MASK_KHR, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT_KHR,
            EGL_NONE,
        ))
        eglContext = eglCreateContext.call(eglDisplay, config, MemorySegment.NULL, ctxAttrs) as MemorySegment
        check(eglContext != MemorySegment.NULL) { "eglCreateContext failed" }
        created = true
    }

    override fun swapBuffers() {
        eglSwapBuffers.call(eglDisplay, eglSurface)
    }

    override fun createDirectContext(): DirectContext {
        check(created) { "Wayland EGL context has not been created" }
        val glInterface = skiaGlInterface ?: GLAssembledInterface.createFromNativePointers(
            MemorySegment.NULL.address(),
            skiaGlProcResolver.address(),
        ).also { skiaGlInterface = it }
        return DirectContext.makeGLWithInterface(glInterface)
    }

    override fun resize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        width = widthPx
        height = heightPx
        if (eglWindow != MemorySegment.NULL) {
            wlEglWindowResize.call(eglWindow, widthPx, heightPx, 0, 0)
        }
    }

    override fun drawableSize(): GlContext.Size = GlContext.Size(width, height)

    override fun dispose() {
        runCatching { skiaGlInterface?.close() }
        runCatching { eglMakeCurrent.call(eglDisplay, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL) }
        runCatching { if (eglSurface != MemorySegment.NULL) eglDestroySurface.call(eglDisplay, eglSurface) }
        runCatching { if (eglContext != MemorySegment.NULL) eglDestroyContext.call(eglDisplay, eglContext) }
        runCatching { if (eglWindow != MemorySegment.NULL) wlEglWindowDestroy.call(eglWindow) }
    }

    private fun intSegment(values: IntArray): MemorySegment {
        val seg = arena.allocate(JAVA_INT.byteSize() * values.size)
        values.forEachIndexed { i, v -> seg.set(JAVA_INT, JAVA_INT.byteSize() * i, v) }
        return seg
    }

    private fun eglString(name: Int): String =
        cString(eglQueryString.call(eglDisplay, name) as MemorySegment)

    private fun glString(name: Int): String =
        cString(glGetString.call(name) as MemorySegment)

    private fun reportDriverOnce() {
        if (diagnosticsReported || System.getenv("KADRE_DIAGNOSE_WAYLAND_EGL") != "1") return
        diagnosticsReported = true
        println(
            "[wayland-egl] EGL vendor=${eglString(EGL_VENDOR)}, version=${eglString(EGL_VERSION)}, " +
                "client APIs=${eglString(EGL_CLIENT_APIS)}",
        )
        println(
            "[wayland-egl] GL vendor=${glString(GL_VENDOR)}, renderer=${glString(GL_RENDERER)}, " +
                "version=${glString(GL_VERSION)}",
        )
    }

    private fun cString(pointer: MemorySegment): String =
        if (pointer == MemorySegment.NULL) "<null>" else pointer.reinterpret(1024).getString(0)

    private val skiaGlProcResolver: MemorySegment by lazy {
        val resolver = MethodHandles.lookup().findVirtual(
            WaylandEglContext::class.java,
            "resolveSkiaGlProc",
            MethodType.methodType(
                MemorySegment::class.java,
                MemorySegment::class.java,
                MemorySegment::class.java,
            ),
        ).bindTo(this)
        NativeFfi.linker.upcallStub(
            resolver,
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
            arena,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveSkiaGlProc(context: MemorySegment, name: MemorySegment): MemorySegment =
        runCatching {
            val eglProc = eglGetProcAddress.call(name) as MemorySegment
            if (eglProc != MemorySegment.NULL) eglProc
            else gl.find(cString(name)).orElse(MemorySegment.NULL)
        }.getOrDefault(MemorySegment.NULL)

    private companion object {
        val egl = NativeFfi.lookup("EGL", "libEGL.so.1", "libEGL.so")
        val gl = NativeFfi.lookup("GL", "libGL.so.1", "libGL.so")
        val wlEgl = NativeFfi.lookup("wayland-egl", "libwayland-egl.so.1", "libwayland-egl.so")

        val wlEglWindowCreate = NativeFfi.handle(wlEgl, "wl_egl_window_create", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT))
        val wlEglWindowResize = NativeFfi.handle(wlEgl, "wl_egl_window_resize", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT))
        val wlEglWindowDestroy = NativeFfi.handle(wlEgl, "wl_egl_window_destroy", FunctionDescriptor.ofVoid(ADDRESS))

        val eglGetDisplay = NativeFfi.handle(egl, "eglGetDisplay", FunctionDescriptor.of(ADDRESS, ADDRESS))
        val eglInitialize = NativeFfi.handle(egl, "eglInitialize", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS))
        val eglBindAPI = NativeFfi.handle(egl, "eglBindAPI", FunctionDescriptor.of(JAVA_INT, JAVA_INT))
        val eglChooseConfig = NativeFfi.handle(egl, "eglChooseConfig", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS))
        val eglCreateWindowSurface = NativeFfi.handle(egl, "eglCreateWindowSurface", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
        val eglCreateContext = NativeFfi.handle(egl, "eglCreateContext", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
        val eglMakeCurrent = NativeFfi.handle(egl, "eglMakeCurrent", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
        val eglGetError = NativeFfi.handle(egl, "eglGetError", FunctionDescriptor.of(JAVA_INT))
        val eglGetProcAddress = NativeFfi.handle(egl, "eglGetProcAddress", FunctionDescriptor.of(ADDRESS, ADDRESS))
        val eglQueryString = NativeFfi.handle(egl, "eglQueryString", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT))
        val eglSwapBuffers = NativeFfi.handle(egl, "eglSwapBuffers", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS))
        val eglSwapInterval = NativeFfi.handle(egl, "eglSwapInterval", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))
        val eglDestroySurface = NativeFfi.handle(egl, "eglDestroySurface", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS))
        val eglDestroyContext = NativeFfi.handle(egl, "eglDestroyContext", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS))
        val glGetString = NativeFfi.handle(gl, "glGetString", FunctionDescriptor.of(ADDRESS, JAVA_INT))
    }
}

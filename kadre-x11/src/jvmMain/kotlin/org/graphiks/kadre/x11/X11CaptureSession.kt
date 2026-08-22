package org.graphiks.kadre.x11.capture

import org.graphiks.kadre.core.PhysicalSize
import org.graphiks.kadre.core.capture.*
import org.graphiks.kadre.x11.binding.*
import org.graphiks.kadre.x11.binding.capture.*
import org.graphiks.kffi.x11.generated.XShmSegmentInfoCompat
import org.graphiks.kffi.posix.LinuxPosix
import kotlinx.coroutines.*
import org.graphiks.kadre.core.capture.CaptureConfig
import org.graphiks.kadre.core.capture.CaptureError
import org.graphiks.kadre.core.capture.CaptureFrame
import org.graphiks.kadre.core.capture.CaptureSession
import org.graphiks.kadre.core.capture.CaptureSource
import org.graphiks.kadre.core.capture.PixelFormat
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

class X11CaptureSession(
    source: CaptureSource,
    config: CaptureConfig,
    internal val displayPtr: Long,
) : CaptureSession(source, config) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val display = MemorySegment.ofAddress(displayPtr)

    private val shmInfo: ShmResources?

    init {
        shmInfo = try {
            val resolution = resolveSourceSize(source)
            if (resolution != null) {
                createShmResources(display, resolution)
            } else null
        } catch (_: Throwable) { null }

        scope.launch {
            while (isActive) {
                try {
                    val frame = captureFrame()
                    if (frame != null) {
                        _frames.tryEmit(frame)
                    }
                } catch (_: Throwable) {
                    // Silently skip failed frames
                }
                val delayMs = if (config.frameRate > 0) 1000L / config.frameRate else 33L
                delay(delayMs)
            }
        }
    }

    private fun resolveSourceSize(src: CaptureSource): PhysicalSize<Int>? {
        return try {
            Arena.ofConfined().use { arena ->
                val xGetGeom = xGetGeometry ?: return@use null
                val rootRet = arena.allocate(ValueLayout.JAVA_LONG)
                val xRet = arena.allocate(ValueLayout.JAVA_INT)
                val yRet = arena.allocate(ValueLayout.JAVA_INT)
                val wRet = arena.allocate(ValueLayout.JAVA_INT)
                val hRet = arena.allocate(ValueLayout.JAVA_INT)
                val bwRet = arena.allocate(ValueLayout.JAVA_INT)
                val depthRet = arena.allocate(ValueLayout.JAVA_INT)

                val drawable = when (src) {
                    is CaptureSource.Display -> {
                        val rootFn = xDefaultRootWindow ?: return@use null
                        try { rootFn.invokeExact(display) as Long } catch (_: Throwable) { return@use null }
                    }
                    is CaptureSource.Window -> src.id
                }

                val status = xGetGeom.invokeExact(
                    display, drawable,
                    rootRet, xRet, yRet, wRet, hRet, bwRet, depthRet,
                ) as Int
                if (status == 0) return@use null

                val width = wRet.get(ValueLayout.JAVA_INT, 0L)
                val height = hRet.get(ValueLayout.JAVA_INT, 0L)
                if (width <= 0 || height <= 0) return@use null

                PhysicalSize(width, height)
            }
        } catch (_: Throwable) { null }
    }

    private fun captureFrame(): CaptureFrame? {
        val resources = shmInfo ?: return captureFallback()
        val xShmGet = xShmGetImage ?: return captureFallback()
        val src = source

        val drawable = when (src) {
            is CaptureSource.Display -> {
                val rootFn = xDefaultRootWindow ?: return null
                try { rootFn.invokeExact(display) as Long } catch (_: Throwable) { return null }
            }
            is CaptureSource.Window -> {
                val composite = xCompositeNameWindowPixmap
                if (composite != null) {
                    try {
                        val pixmap = composite.invokeExact(display, src.id) as Long
                        if (pixmap != 0L) {
                            val frame = captureFromPixmap(pixmap, resources)
                            try { xFreePixmap?.invokeExact(display, pixmap) } catch (_: Throwable) {}
                            if (frame != null) return frame
                        }
                    } catch (_: Throwable) {}
                }
                src.id
            }
        }

        return synchronized(resources) {
            val status = xShmGet.invokeExact(
                display, drawable, resources.image,
                0, 0, Xlib_AllPlanes,
            ) as Int
            if (status == 0) return@synchronized null

            val stride = resources.width * 4
            val dataSize = stride * resources.height
            val pixelData = ByteArray(dataSize)
            val shmAddr = resources.shmAddr.reinterpret(dataSize.toLong())
            MemorySegment.copy(shmAddr, ValueLayout.JAVA_BYTE, 0, pixelData, 0, dataSize)

            val finalData = if (config.pixelFormat == PixelFormat.RGBA8) {
                bgraToRgba(pixelData)
            } else pixelData

            CaptureFrame(
                size = PhysicalSize(resources.width, resources.height),
                format = config.pixelFormat,
                stride = stride,
                data = finalData,
                timestampNanos = System.nanoTime(),
            )
        }
    }

    private fun captureFromPixmap(pixmap: Long, resources: ShmResources): CaptureFrame? {
        val xShmGet = xShmGetImage ?: return null
        return synchronized(resources) {
            val status = xShmGet.invokeExact(
                display, pixmap, resources.image,
                0, 0, Xlib_AllPlanes,
            ) as Int
            if (status == 0) return@synchronized null

            val stride = resources.width * 4
            val dataSize = stride * resources.height
            val pixelData = ByteArray(dataSize)
            val shmAddr = resources.shmAddr.reinterpret(dataSize.toLong())
            MemorySegment.copy(shmAddr, ValueLayout.JAVA_BYTE, 0, pixelData, 0, dataSize)

            val finalData = if (config.pixelFormat == PixelFormat.RGBA8) {
                bgraToRgba(pixelData)
            } else pixelData

            CaptureFrame(
                size = PhysicalSize(resources.width, resources.height),
                format = config.pixelFormat,
                stride = stride,
                data = finalData,
                timestampNanos = System.nanoTime(),
            )
        }
    }

    private fun captureFallback(): CaptureFrame? {
        val xGet = xGetImage ?: return null
        val xGetGeom = xGetGeometry ?: return null
        val src = source

        return try {
            Arena.ofConfined().use { arena ->
                val result = captureFallbackInner(arena, src, xGet, xGetGeom)
                result
            }
        } catch (_: Throwable) { null }
    }

    private fun captureFallbackInner(
        arena: Arena,
        src: CaptureSource,
        xGet: java.lang.invoke.MethodHandle,
        xGetGeom: java.lang.invoke.MethodHandle,
    ): CaptureFrame? {
        val rootRet = arena.allocate(ValueLayout.JAVA_LONG)
        val xRet = arena.allocate(ValueLayout.JAVA_INT)
        val yRet = arena.allocate(ValueLayout.JAVA_INT)
        val wRet = arena.allocate(ValueLayout.JAVA_INT)
        val hRet = arena.allocate(ValueLayout.JAVA_INT)
        val bwRet = arena.allocate(ValueLayout.JAVA_INT)
        val depthRet = arena.allocate(ValueLayout.JAVA_INT)

        val rootFn = xDefaultRootWindow ?: return null
        val drawable = when (src) {
            is CaptureSource.Display -> {
                try { rootFn.invokeExact(display) as Long } catch (_: Throwable) { return null }
            }
            is CaptureSource.Window -> src.id
        }

        val geomStatus = xGetGeom.invokeExact(
            display, drawable,
            rootRet, xRet, yRet, wRet, hRet, bwRet, depthRet,
        ) as Int
        if (geomStatus == 0) return null

        val width = wRet.get(ValueLayout.JAVA_INT, 0L)
        val height = hRet.get(ValueLayout.JAVA_INT, 0L)
        if (width <= 0 || height <= 0) return null

        val imagePtr = xGet.invokeExact(
            display, drawable,
            0, 0, width, height,
            Xlib_AllPlanes, Xlib_ZPixmap,
        ) as MemorySegment
        if (imagePtr == MemorySegment.NULL || imagePtr.address() == 0L) return null

        val result: CaptureFrame? = readXImage(imagePtr, width, height)
        try { xDestroyImage?.invokeExact(imagePtr) } catch (_: Throwable) {}
        return result
    }

    private fun readXImage(imagePtr: MemorySegment, width: Int, height: Int): CaptureFrame? {
        val image = imagePtr.reinterpret(64L)
        val dataPtr = image.get(ValueLayout.ADDRESS, XIMAGE_DATA_OFFSET)
        val bytesPerLine = image.get(ValueLayout.JAVA_INT, XIMAGE_BYTES_PER_LINE_OFFSET)
        if (dataPtr == MemorySegment.NULL || dataPtr.address() == 0L) return null

        val stride = if (bytesPerLine > 0) bytesPerLine else width * 4
        val dataSize = stride * height
        val pixelData = ByteArray(dataSize)
        val srcData = dataPtr.reinterpret(dataSize.toLong())
        MemorySegment.copy(srcData, ValueLayout.JAVA_BYTE, 0, pixelData, 0, dataSize)

        val finalData = if (config.pixelFormat == PixelFormat.RGBA8) {
            bgraToRgba(pixelData)
        } else pixelData

        return CaptureFrame(
            size = PhysicalSize(width, height),
            format = config.pixelFormat,
            stride = stride,
            data = finalData,
            timestampNanos = System.nanoTime(),
        )
    }

    override fun close() {
        scope.cancel()
        shmInfo?.let { destroyShmResources(display, it) }
    }
}

// ── Shared memory management ────────────────────────────────────────────────────

internal class ShmResources(
    val width: Int,
    val height: Int,
    val image: MemorySegment,      // XImage*
    val shmAddr: MemorySegment,    // pointer to shared memory data
    val shmid: Int,                // shared memory ID (for cleanup)
    val shminfo: MemorySegment,    // XShmSegmentInfo struct
)

internal fun createShmResources(
    display: MemorySegment,
    size: PhysicalSize<Int>,
): ShmResources? {
    val attach = xShmAttach ?: return null
    val createImage = xShmCreateImage ?: return null
    val defVisual = xDefaultVisual ?: return null
    val defDepth = xDefaultDepth ?: return null
    val defScreen = xDefaultScreen ?: return null
    return try {
        val width = size.width
        val height = size.height
        val stride = width * 4
        val shmSize = stride * height

        val screen = defScreen.invokeExact(display) as Int
        val visual = defVisual.invokeExact(display, screen) as MemorySegment
        val depth = defDepth.invokeExact(display, screen) as Int

        // Create shared memory segment
        val shmid = LinuxPosix.shmget(
            LinuxPosix.IPC_PRIVATE,
            shmSize.toLong(),
            LinuxPosix.IPC_CREAT or 384,
        )

        val shmaddr = LinuxPosix.shmat(shmid, MemorySegment.NULL, 0)
        if (shmaddr == MemorySegment.NULL || shmaddr.address() == 0L) {
            runCatching { LinuxPosix.shmctl(shmid, LinuxPosix.IPC_RMID) }
            return null
        }

        Arena.ofConfined().use { arena ->
            val shminfoBinding = XShmSegmentInfoCompat()
            val shminfo = XShmSegmentInfoCompat.Companion.allocate(arena)
            shminfoBinding.shmseg(shminfo, 0L) // server assigns the segment id
            shminfoBinding.shmid(shminfo, shmid)
            shminfoBinding.readOnly(shminfo, 0)
            shminfoBinding.shmaddr(shminfo, shmaddr)

            val image = createImage.invokeExact(
                display, visual, depth, XSHM_ZPIXMAP,
                shmaddr, shminfo, width, height,
            ) as MemorySegment

            if (image == MemorySegment.NULL || image.address() == 0L) {
                runCatching { LinuxPosix.shmdt(shmaddr) }
                runCatching { LinuxPosix.shmctl(shmid, LinuxPosix.IPC_RMID) }
                return null
            }

            val attachStatus = attach.invokeExact(display, shminfo) as Int
            if (attachStatus == 0) {
                try { xDestroyImage?.invokeExact(image) } catch (_: Throwable) {}
                runCatching { LinuxPosix.shmdt(shmaddr) }
                runCatching { LinuxPosix.shmctl(shmid, LinuxPosix.IPC_RMID) }
                return null
            }

            // Copy shminfo data since the arena will close
            val persistentInfo = XShmSegmentInfoCompat.Companion.allocate(Arena.global())
            persistentInfo.copyFrom(shminfo)

            ShmResources(
                width = width,
                height = height,
                image = image,
                shmAddr = shmaddr,
                shmid = shmid,
                shminfo = persistentInfo,
            )
        }
    } catch (_: Throwable) { null }
}

internal fun destroyShmResources(display: MemorySegment, resources: ShmResources) {
    try { xShmDetach?.invokeExact(display, resources.shminfo) } catch (_: Throwable) {}
    try { xDestroyImage?.invokeExact(resources.image) } catch (_: Throwable) {}
    runCatching { LinuxPosix.shmdt(resources.shmAddr) }
    runCatching { LinuxPosix.shmctl(resources.shmid, LinuxPosix.IPC_RMID) }
}

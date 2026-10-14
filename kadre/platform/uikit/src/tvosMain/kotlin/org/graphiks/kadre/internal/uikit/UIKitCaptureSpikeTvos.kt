@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.graphiks.kadre.internal.uikit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import platform.CoreGraphics.CGImageGetBitmapInfo
import platform.CoreGraphics.CGImageGetBytesPerRow
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetPixelFormatType
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.Foundation.NSRunLoop
import platform.Foundation.NSSelectorFromString
import platform.ReplayKit.RPScreenRecorder
import platform.QuartzCore.CADisplayLink
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIView

/**
 * Probe de COMPILE tvOS (phase 9, Task 1) — le MIROIR iOS, les MÊMES formes contre le klib
 * tvOS 2.4.20 (AppleTVSimulator27.0). Le klib tvOS porte RÉELLEMENT le chemin ReplayKit
 * capture (strings knm vérifiées, § rapport) : `startCaptureWithHandler:completionHandler:`,
 * `stopCaptureWithHandler`, `sharedRecorder`, `isAvailable` — la disipline tvOS du ruling
 * est tenue : klib-bound ≠ runtime-proven (le compile + Simulator bornent la preuve, le
 * device reste le charter manuel).
 *
 * Les ABSENS tvOS (jamais référencés ici ni ailleurs) :
 * - `cameraEnabled`/`setCameraEnabled` : 0 hit dans le knm tvOS — ET le ruling mic/camera
 *   ne les activerait de toute façon sur AUCUNE cible.
 * - `RPSystemBroadcastPickerView` : absent du klib tvOS — et le ruling l'interdit partout
 *   (les extensions broadcast = process séparé, les frames n'atteignent jamais l'app).
 */
internal object UIKitCaptureSpikeTvos {

    /** Le fait de disponibilité brut du recorder partagé — klib-présent des deux cibles. */
    fun recorderIsAvailable(): Boolean = RPScreenRecorder.sharedRecorder().isAvailable()

    /**
     * La forme EXACTE du démarrage ReplayKit — la MÊME signature klib qu'iOS
     * (`(CMSampleBufferRef?, Long, NSError?)`) : aucune couture de source set n'est
     * nécessaire pour la signature, seulement pour la disponibilité runtime.
     */
    fun startCapture(
        captureHandler: (CMSampleBufferRef?, Long, NSError?) -> Unit,
        completionHandler: (NSError?) -> Unit,
    ) {
        RPScreenRecorder.sharedRecorder().startCaptureWithHandler(
            captureHandler = captureHandler,
            completionHandler = completionHandler,
        )
    }

    /** La forme de l'arrêt — identique qu'iOS. */
    fun stopCapture(completionHandler: (NSError?) -> Unit) {
        RPScreenRecorder.sharedRecorder().stopCaptureWithHandler(completionHandler)
    }

    /** `CMSampleBufferGetImageBuffer` : le pixel buffer d'un sample, ou rien. */
    fun sampleBufferPixelBuffer(sampleBuffer: CMSampleBufferRef): CVPixelBufferRef? =
        CMSampleBufferGetImageBuffer(sampleBuffer)

    /** La forme lock/read/unlock — miroir iOS exact (klib CoreVideo des deux cibles). */
    @OptIn(ExperimentalForeignApi::class)
    fun pixelBufferFacts(pixelBuffer: CVPixelBufferRef): UIKitCaptureSpikeBufferFacts? {
        CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        try {
            val base = CVPixelBufferGetBaseAddress(pixelBuffer) ?: return null
            val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
            val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
            val cvFormat = CVPixelBufferGetPixelFormatType(pixelBuffer).toLong()
            val bytes = base.reinterpret<ByteVarOf<Byte>>().readBytes(height * bytesPerRow)
            return UIKitCaptureSpikeBufferFacts(width, height, bytesPerRow, cvFormat, bytes)
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        }
    }

    /** La composition couture → kernels PURS — les MÊMES kernels uikitMain des deux cibles. */
    fun portableLayoutOf(facts: UIKitCaptureSpikeBufferFacts): Pair<PixelFormat, PixelPlaneLayout>? =
        uikitCaptureLayoutOf(
            width = facts.width,
            height = facts.height,
            bytesPerRow = facts.bytesPerRow,
            bitmapInfoIsBgra = facts.cvFormat == UIKIT_CVFORMAT_32BGRA,
        )

    fun colorEncodingOf(facts: UIKitCaptureSpikeBufferFacts): ColorEncoding =
        uikitCaptureColorEncodingOf(facts.cvFormat)

    /**
     * La forme du snapshot renderer — `UIGraphicsImageRenderer` + `drawViewHierarchyInRect`
     * sont klib-présents tvOS (strings knm vérifiées) : la pompe surface du ruling est
     * both-platform, la preuve end-to-end Simulator peut donc exister des DEUX familles.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun rendererSnapshotFacts(view: UIView): UIKitCaptureSpikeBitmapFacts? {
        val renderer = UIGraphicsImageRenderer(bounds = view.bounds, format = UIGraphicsImageRendererFormat.defaultFormat())
        val image = renderer.imageWithActions { _ ->
            view.drawViewHierarchyInRect(view.bounds, afterScreenUpdates = true)
        }
        val cgImage = image.CGImage ?: return null
        return UIKitCaptureSpikeBitmapFacts(
            width = CGImageGetWidth(cgImage).toInt(),
            height = CGImageGetHeight(cgImage).toInt(),
            bytesPerRow = CGImageGetBytesPerRow(cgImage).toInt(),
            bitmapInfoRawValue = CGImageGetBitmapInfo(cgImage).toLong() and 0xFFFF_FFFFL,
        )
    }

    /** La forme CADisplayLink (repli documenté, § décision 5 du rapport) — klib-présent tvOS. */
    fun displayLinkShape(onFrame: (CADisplayLink) -> Unit): CADisplayLink {
        val link = CADisplayLink.displayLinkWithTarget(
            target = UIKitCaptureSpikeDisplayLinkTarget(onFrame),
            selector = NSSelectorFromString("stepFrame:"),
        )
        link.addToRunLoop(NSRunLoop.mainRunLoop, NSDefaultRunLoopMode!!)
        return link
    }
}

/** Les scalaires bruts d'UN pixel buffer ReplayKit — zéro type SDK (pattern jeton). */
internal class UIKitCaptureSpikeBufferFacts(
    val width: Int,
    val height: Int,
    val bytesPerRow: Int,
    val cvFormat: Long,
    val bytes: ByteArray,
)

/** Les scalaires bruts d'UN bitmap renderer — le bitmap info reste BRUT (lu, pas tranché). */
internal class UIKitCaptureSpikeBitmapFacts(
    val width: Int,
    val height: Int,
    val bytesPerRow: Int,
    val bitmapInfoRawValue: Long,
)

/** La cible @ObjCAction du probe display link (pattern [UIKitGestureBindings]). */
internal class UIKitCaptureSpikeDisplayLinkTarget(
    private val onFrame: (CADisplayLink) -> Unit,
) : NSObject() {
    @ObjCAction
    fun stepFrame(sender: CADisplayLink) {
        onFrame(sender)
    }
}

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
 * Probe de COMPILE iOS (phase 9, Task 1) : les formes d'interop K/N EXACTES du chemin
 * capture — chaque fonction prouve qu'un appel donné type-checke contre le klib iOS
 * 2.4.20 (iPhoneSimulator27.0) et note la forme pour les Tasks 2-4 (devoir :
 * consommer ou supprimer). Zéro appel n'est tiré au chargement : ce fichier ne DOIT rien
 * faire au runtime — les fonctions ne s'exécutent que si une task suivante les appelle.
 *
 * Formes relevées (spike) :
 * - `RPScreenRecorder.sharedRecorder()` est une propriété de classe ; `isAvailable` un
 *   `Boolean` non-null. (`cameraEnabled` existe aussi côté klib iOS — HORS périmètre :
 *   le ruling mic/camera ne l'active jamais.)
 * - `startCaptureWithHandler(captureHandler:completionHandler:)` : le handler klib porte
 *   `(CMSampleBufferRef?, Long, NSError?)` — le timing d'échantillon arrive en `Long`.
 * - `CVPixelBuffer*` prennent/rendent des `CVPixelBufferRef?` ; `lock` porte la constante
 *   `kCVPixelBufferLock_ReadOnly` telle quelle ; `GetBaseAddress` rend un pointeur à
 *   `reinterpret<ByteVarOf<Byte>>()` + `readBytes(n)` (le memcpy honnête — JAMAIS la
 *   copie octet-par-octet du kadre-old, l'anti-pattern listé).
 * - `UIGraphicsImageRenderer(bounds:format:)` + `imageWithActions { }` +
 *   `view.drawViewHierarchyInRect(view.bounds, afterScreenUpdates = true)` ; le bitmap
 *   info du `CGImage` produit se lit par les fonctions C `CGImageGetWidth/GetHeight/
 *   GetBytesPerRow/GetBitmapInfo` (klib CoreGraphics) — JAMAIS supposé (§ décision 3).
 * - `CADisplayLink.displayLinkWithTarget(target:selector:)` (klib UIKit) +
 *   `addToRunLoop(runloop, mode)` (déclaré dans le klib QUARTZCORE — le même FQN
 *   fusionne à travers les deux klibs, UIKit dépend de QuartzCore) ; le cible porte un
 *   `@ObjCAction` (pattern [UIKitGestureBindings]). La DÉCISION de cadence (§ rapport,
 *   décision 5) préfère le ticker manuel injecté — ce probe reste comme forme de repli.
 */
internal object UIKitCaptureSpikeIos {

    /** Le fait de disponibilité brut du recorder partagé — `false` attendu sur Simulator. */
    fun recorderIsAvailable(): Boolean = RPScreenRecorder.sharedRecorder().isAvailable()

    /**
     * La forme EXACTE du démarrage ReplayKit — la lambda captureHandler porte la signature
     * klib `(CMSampleBufferRef?, Long, NSError?)` ; l'erreur de COMPLETION (le démarrage a
     * échoué) et l'erreur du HANDLER (le flux vivant a échoué) sont routées séparément,
     * la matière première du mapping terminal de la Task 3 (§ décision du rapport).
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

    /** La forme de l'arrêt — l'erreur d'arrêt est un fait diagnostic, non un échec du flux. */
    fun stopCapture(completionHandler: (NSError?) -> Unit) {
        RPScreenRecorder.sharedRecorder().stopCaptureWithHandler(completionHandler)
    }

    /** `CMSampleBufferGetImageBuffer` : le pixel buffer d'un sample, ou rien. */
    fun sampleBufferPixelBuffer(sampleBuffer: CMSampleBufferRef): CVPixelBufferRef? =
        CMSampleBufferGetImageBuffer(sampleBuffer)

    /**
     * La forme lock/read/unlock — les scalaires pré-extraits sortent, le buffer est copié
     * par `readBytes` (memcpy) sous lock readonly, l'unlock en `finally` (le `?: return`
     * du base address passe par le finally comme tout le reste).
     */
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

    /**
     * La composition couture → kernels PURS (le chemin que la Task 2 consommera) : les
     * scalaires du pixel buffer entrent dans [uikitCaptureLayoutOfFourcc] — la PORTE fourcc
     * qui n'admet que 'BGRA'/'RGBA' (revue Important-2 : 'ARGB'/'ABGR' → `null`, jamais
     * réétiquetés) — et dans [uikitCaptureColorEncodingOf] ; la paire portable en sort,
     * `null` honnête si la combinaison n'est pas la table.
     */
    fun portableLayoutOf(facts: UIKitCaptureSpikeBufferFacts): Pair<PixelFormat, PixelPlaneLayout>? =
        uikitCaptureLayoutOfFourcc(
            width = facts.width,
            height = facts.height,
            bytesPerRow = facts.bytesPerRow,
            cvFormat = facts.cvFormat,
        )

    fun colorEncodingOf(facts: UIKitCaptureSpikeBufferFacts): ColorEncoding =
        uikitCaptureColorEncodingOf(facts.cvFormat)

    /**
     * La forme du snapshot renderer (le ruling surface) : le bitmap info du `CGImage`
     * produit est LU par les fonctions C (klib CoreGraphics) et porté brut — la décision
     * Bgra8/Rgba8/Bgrx8 appartient à la couture de la Task 3 qui réduira ce raw value.
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

    /** La forme CADisplayLink (repli documenté, § décision 5 du rapport). */
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

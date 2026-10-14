package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kadre.capture.AlphaMode
import org.graphiks.kadre.capture.CaptureOrientation
import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.TransferFunction
import org.graphiks.kadre.internal.runtime.CapturePortFrame
import org.graphiks.kadre.internal.runtime.CapturePortPlane
import org.graphiks.kadre.surface.PhysicalSize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Les kernels PURS de capture (phase 9, Task 1) : la table de layout portable
 * (`uikitCaptureLayoutOf` — le pixel buffer ReplayKit → `PixelFormat` + `PixelPlaneLayout`),
 * l'encodage couleur (`uikitCaptureColorEncodingOf` — le code fourcc `CVPixelBuffer` →
 * `ColorEncoding`, `Unknown` honnête), et la pompe surface (`UIKitSurfaceFrameFacts` →
 * `uikitSurfaceLayoutOf`) — prouvés SANS AUCUN type SDK (le pattern jeton du module : les
 * scalaires pré-extraits entrent, les formes foundation sortent).
 *
 * La garde de fabrication (Step 1 du brief) : le layout BGRA serré ET le stride paddé
 * passent (les rangées de padding sont LÉGALES — la formule plancher du layout tient) ;
 * toute combinaison malhonnête répond `null`, JAMAIS un layout approximé. Les paires
 * produites sont ré-admises par le validateur runtime (`CapturePortFrame` — la table
 * portable de `CapturePort.kt:176-231`) : un kernel qui fabriquerait un layout hors table
 * échouerait à l'init du frame, jamais au collecteur.
 */
class UIKitCaptureKernelsTest {

    // MARK: - layout : la table portable (pixel buffer ReplayKit 32bpp)

    @Test
    fun bgraLayoutPassesThePortableTable() {
        val (format, layout) = checkNotNull(uikitCaptureLayoutOf(1920, 1080, 7680, bitmapInfoIsBgra = true))
        assertEquals(PixelFormat.Bgra8, format, "le bitmap alpha-FIRST little-endian est BGRA — lu, jamais supposé")
        assertEquals(1920, layout.width)
        assertEquals(1080, layout.height)
        assertEquals(7680, layout.rowStride, "le bytesPerRow du CVPixelBuffer EST le rowStride")
        assertEquals(4, layout.pixelStride, "32bpp packed : pixelStride 4 (la table portable)")
        assertEquals(1, layout.horizontalSubsampling, "packed RGB : AUCUNE sous-échantillonnage")
        assertEquals(1, layout.verticalSubsampling)
        assertEquals(1920 * 1080 * 4, layout.byteCount, "serré : byteCount = h × rowStride")
    }

    @Test
    fun rgbaLayoutPassesWhenTheBitmapIsAlphaLast() {
        val (format, layout) = checkNotNull(uikitCaptureLayoutOf(640, 480, 2560, bitmapInfoIsBgra = false))
        assertEquals(PixelFormat.Rgba8, format)
        assertEquals(2560, layout.rowStride)
        assertEquals(640 * 480 * 4, layout.byteCount)
    }

    @Test
    fun paddedRowStrideIsCarriedVerbatimAndCountedInByteCount() {
        // L'honnêteté bytesPerRow : un stride paddé (64 octets de padding par rangée) est
        // LÉGAL — le layout le PORTE tel quel (rowStride = 448) et le byteCount compte les
        // rangées paddées EN ENTIER (le port copie des rangées complètes, jamais un buffer
        // « serré » réinventé). La formule plancher de PixelPlaneLayout tient :
        // (h-1)·rowStride + (w-1)·pixelStride + 1 = 2·448 + 3·4 + 1 = 909 ≤ 1344.
        val (_, layout) = checkNotNull(uikitCaptureLayoutOf(111, 3, 448, bitmapInfoIsBgra = true))
        assertEquals(448, layout.rowStride, "448 = 444 + 4 : un pixel de padding par rangée")
        assertEquals(3 * 448, layout.byteCount)
        val floor = 2L * 448L + 110L * 4L + 1L
        assertTrue(layout.byteCount.toLong() >= floor, "le plancher du layout tient sur stride paddé")
    }

    @Test
    fun rowStrideMayCarryNonPixelAlignedPadding() {
        // Un stride NON aligné pixel (4·w + 3) reste portable : le pixel i de la rangée j
        // vit à j·rowStride + i·4 — la table ne fabrique rien de plus.
        val (_, layout) = checkNotNull(uikitCaptureLayoutOf(100, 2, 403, bitmapInfoIsBgra = false))
        assertEquals(403, layout.rowStride)
        assertEquals(806, layout.byteCount)
    }

    @Test
    fun dishonestLayoutCombinationsAreRejectedNeverApproximated() {
        assertNull(uikitCaptureLayoutOf(0, 100, 400, bitmapInfoIsBgra = true), "largeur nulle")
        assertNull(uikitCaptureLayoutOf(100, 0, 400, bitmapInfoIsBgra = true), "hauteur nulle")
        assertNull(uikitCaptureLayoutOf(-1, 100, 400, bitmapInfoIsBgra = true), "largeur négative")
        assertNull(uikitCaptureLayoutOf(100, 100, 0, bitmapInfoIsBgra = true), "stride nul")
        assertNull(uikitCaptureLayoutOf(100, 100, -400, bitmapInfoIsBgra = true), "stride négatif")
        assertNull(
            uikitCaptureLayoutOf(100, 100, 399, bitmapInfoIsBgra = true),
            "399 < 100·4 : la rangée ne tient pas un pixel 32bpp — malhonnête, jamais resserré",
        )
        assertNull(
            uikitCaptureLayoutOf(100, 100, 4 * 100 - 3, bitmapInfoIsBgra = true),
            "même LE plancher exact du validateur est refusé : la table exige des rangées COMPLÈTES",
        )
        assertNull(
            uikitCaptureLayoutOf(Int.MAX_VALUE / 4 + 1, 1, Int.MAX_VALUE, bitmapInfoIsBgra = true),
            "width·4 déborde Int : aucune rangée complète possible — rejet (arithmétique Long)",
        )
        assertNull(
            uikitCaptureLayoutOf(1, 65536, 32768, bitmapInfoIsBgra = true),
            "h·rowStride déborde Int (2³¹) : le byteCount n'existe pas en Int — rejet",
        )
    }

    // MARK: - layout : la garde de fabrication (ré-admission par le validateur runtime)

    @Test
    fun captureLayoutPairIsAdmittedByTheRuntimePortableTable() {
        // Le contrat complet : la PAIRE du kernel construit un `CapturePortFrame` TEL QUEL —
        // le validateur portable (CapturePort.kt:176-231 : 1 plan, pixelStride 4, pas de
        // sous-échantillonnage) l'admet. Un kernel hors table échouerait ICI, jamais au
        // collecteur en vol.
        val (format, layout) = checkNotNull(uikitCaptureLayoutOf(16, 16, 64, bitmapInfoIsBgra = true))
        val frame = CapturePortFrame(
            size = PhysicalSize(16, 16),
            format = format,
            planes = listOf(CapturePortPlane(layout, ByteArray(layout.byteCount))),
            configurationRevision = 0L,
            sourceTimestamp = Duration.ZERO,
            duration = 1.seconds,
            discontinuity = null,
            colorEncoding = uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_32BGRA),
            alphaMode = AlphaMode.Opaque,
            orientation = CaptureOrientation.Upright,
        )
        assertEquals(PixelFormat.Bgra8, frame.format)
        assertEquals(1, frame.planes.size)
    }

    @Test
    fun captureLayoutPairWithPaddingIsAlsoAdmittedByTheRuntimeTable() {
        val (format, layout) = checkNotNull(uikitCaptureLayoutOf(111, 3, 448, bitmapInfoIsBgra = false))
        val frame = CapturePortFrame(
            size = PhysicalSize(111, 3),
            format = format,
            planes = listOf(CapturePortPlane(layout, ByteArray(layout.byteCount))),
            configurationRevision = 7L,
            sourceTimestamp = Duration.ZERO,
            duration = null,
            discontinuity = null,
            colorEncoding = uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_32RGBA),
            alphaMode = AlphaMode.Opaque,
            orientation = CaptureOrientation.Upright,
        )
        assertEquals(PixelFormat.Rgba8, frame.format)
    }

    @Test
    fun aDishonestByteCountWouldFailAtTheRuntimeValidatorNotSilentlyPass() {
        // Le MIROIR de la garde : un layout dont le byteCount MENT (500 < plancher 797 — la
        // dernière rangée paddée ne tient pas) est rejeté par l'init du layout runtime — la
        // raison pour laquelle les kernels répondent `null` plutôt que d'approcher : un
        // byteCount fabriqué ne franchit même pas la couture.
        assertFailsWith<IllegalArgumentException> {
            PixelPlaneLayout(
                width = 100,
                height = 2,
                rowStride = 400,
                pixelStride = 4,
                byteCount = 500,
                horizontalSubsampling = 1,
                verticalSubsampling = 1,
            )
        }
    }

    // MARK: - encodage couleur : le fourcc CVPixelBuffer → ColorEncoding

    @Test
    fun fourccConstantsAreTheHeaderTruth() {
        // Les décimales connues des headers CoreVideo (kCVPixelFormatType_32BGRA etc.) —
        // une coquille dans un hex littéral serait épinglée ICI.
        assertEquals(0x42475241L, UIKIT_CVFORMAT_32BGRA)
        assertEquals(1111970369L, UIKIT_CVFORMAT_32BGRA)
        assertEquals(0x52474241L, UIKIT_CVFORMAT_32RGBA)
        assertEquals(1380401729L, UIKIT_CVFORMAT_32RGBA)
        assertEquals(0x41524742L, UIKIT_CVFORMAT_32ARGB)
        assertEquals(1095911234L, UIKIT_CVFORMAT_32ARGB)
        assertEquals(0x41424752L, UIKIT_CVFORMAT_32ABGR)
        assertEquals(1094862674L, UIKIT_CVFORMAT_32ABGR)
        assertEquals(0x34323076L, UIKIT_CVFORMAT_420VIDEO)
        assertEquals(875704438L, UIKIT_CVFORMAT_420VIDEO)
        assertEquals(0x34323066L, UIKIT_CVFORMAT_420FULL)
        assertEquals(875704422L, UIKIT_CVFORMAT_420FULL)
    }

    @Test
    fun packedRgbFourccsMapToSrgbBt709IdentityFull() {
        // La décision spike (§ rapport, décision 2) : un packed 8-bit RGB porte sRGB
        // (transfer), Bt709 (primaries — les chromaticités sRGB SONT celles du BT.709,
        // ITU-R BT.715 : identité numérique, pas une approximation), Identity (données RGB :
        // AUCUNE matrice YCbCr) et Full (8-bit RGB 0-255 par définition), sans HDR.
        val expected = ColorEncoding(
            primaries = ColorPrimaries.Bt709,
            transfer = TransferFunction.Srgb,
            matrix = MatrixCoefficients.Identity,
            range = ColorRange.Full,
            hdr = HdrMetadata.None,
        )
        assertEquals(expected, uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_32BGRA))
        assertEquals(expected, uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_32RGBA))
        assertEquals(expected, uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_32ARGB))
        assertEquals(expected, uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_32ABGR))
    }

    @Test
    fun biPlanarVideoRangeCodePinsOnlyTheRange() {
        // '420v' : le suffixe v EST le fait range (16-235 vidéo) — le code NE PORTE NI
        // primaires NI transfert NI matrice (601 vs 709 dépend des tags du buffer, pas du
        // fourcc) : Unknown honnête pour le reste.
        val encoding = uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_420VIDEO)
        assertEquals(ColorPrimaries.Unknown, encoding.primaries)
        assertEquals(TransferFunction.Unknown, encoding.transfer)
        assertEquals(MatrixCoefficients.Unknown, encoding.matrix)
        assertEquals(ColorRange.Limited, encoding.range)
        assertEquals(HdrMetadata.Unknown, encoding.hdr)
    }

    @Test
    fun biPlanarFullRangeCodePinsOnlyTheRange() {
        val encoding = uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_420FULL)
        assertEquals(ColorRange.Full, encoding.range)
        assertEquals(ColorPrimaries.Unknown, encoding.primaries)
        assertEquals(TransferFunction.Unknown, encoding.transfer)
        assertEquals(MatrixCoefficients.Unknown, encoding.matrix)
        assertEquals(HdrMetadata.Unknown, encoding.hdr)
    }

    @Test
    fun unknownFormatCodesAreHonestUnknown() {
        val expected = ColorEncoding(
            primaries = ColorPrimaries.Unknown,
            transfer = TransferFunction.Unknown,
            matrix = MatrixCoefficients.Unknown,
            range = ColorRange.Unknown,
            hdr = HdrMetadata.Unknown,
        )
        assertEquals(expected, uikitCaptureColorEncodingOf(0L))
        assertEquals(expected, uikitCaptureColorEncodingOf(42L))
        assertEquals(expected, uikitCaptureColorEncodingOf(-1L))
        assertEquals(expected, uikitCaptureColorEncodingOf(Long.MIN_VALUE))
        assertEquals(expected, uikitCaptureColorEncodingOf(0x4C303038L), "'L008' — un planar 8-bit luma hors table")
    }

    // MARK: - pompe surface : les faits du renderer → layout

    @Test
    fun surfaceFactsProduceThePackedLayout() {
        val layout = checkNotNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(64, 32, 256, PixelFormat.Bgra8)))
        assertEquals(64, layout.width)
        assertEquals(32, layout.height)
        assertEquals(256, layout.rowStride)
        assertEquals(4, layout.pixelStride)
        assertEquals(1, layout.horizontalSubsampling)
        assertEquals(1, layout.verticalSubsampling)
        assertEquals(64 * 32 * 4, layout.byteCount, "serré : byteCount = h × rowStride = 32 × 256")
    }

    @Test
    fun surfaceLayoutAcceptsTheThreePackedFormatsOnly() {
        assertNotNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(16, 16, 64, PixelFormat.Rgba8)))
        assertNotNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(16, 16, 64, PixelFormat.Bgra8)))
        assertNotNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(16, 16, 64, PixelFormat.Bgrx8)), "noneSkip* sans alpha : BGRX portable")
    }

    @Test
    fun planarOrOpaqueSurfaceFactsAreDishonest() {
        // Un bitmap renderer est TOUJOURS packed 32bpp : un fait planaire ou opaque est un
        // mensonge de couture — `null`, jamais un layout approximé.
        assertNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(16, 16, 32, PixelFormat.Nv12)))
        assertNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(16, 16, 16, PixelFormat.I420)))
        assertNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(16, 16, 32, PixelFormat.Opaque("ios-surface", 1))))
    }

    @Test
    fun dishonestSurfaceFactsAreRejectedNeverApproximated() {
        assertNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(0, 16, 64, PixelFormat.Bgra8)), "largeur nulle")
        assertNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(16, -1, 64, PixelFormat.Bgra8)), "hauteur négative")
        assertNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(16, 16, 63, PixelFormat.Bgra8)), "63 < 64 : stride trop court")
        assertNull(uikitSurfaceLayoutOf(UIKitSurfaceFrameFacts(1, 65536, 32768, PixelFormat.Bgra8)), "byteCount déborde Int")
    }

    @Test
    fun surfaceFactsAreAdmittedByTheRuntimePortableTable() {
        // La garde de fabrication de la POMPE : les faits renderer (bitmap 128×64 @ scale 1,
        // stride paddé) construisent un `CapturePortFrame` TEL QUEL — alpha PREMULTIPLIED
        // (le format renderer UIKit composite en premultiplied, § décision 3 du rapport).
        val facts = UIKitSurfaceFrameFacts(128, 64, 512 + 16, PixelFormat.Bgra8)
        val layout = checkNotNull(uikitSurfaceLayoutOf(facts))
        val frame = CapturePortFrame(
            size = PhysicalSize(facts.width, facts.height),
            format = facts.format,
            planes = listOf(CapturePortPlane(layout, ByteArray(layout.byteCount))),
            configurationRevision = 1L,
            sourceTimestamp = Duration.ZERO,
            duration = 1.seconds / 60,
            discontinuity = null,
            colorEncoding = uikitCaptureColorEncodingOf(UIKIT_CVFORMAT_32BGRA),
            alphaMode = AlphaMode.Premultiplied,
            orientation = CaptureOrientation.Upright,
        )
        assertEquals(PixelFormat.Bgra8, frame.format)
        assertEquals(64 * 528, frame.planes.single().layout.byteCount)
    }
}

package org.graphiks.kadre.internal.uikit

import org.graphiks.kadre.capture.ColorEncoding
import org.graphiks.kadre.capture.ColorPrimaries
import org.graphiks.kadre.capture.HdrMetadata
import org.graphiks.kadre.capture.MatrixCoefficients
import org.graphiks.kadre.capture.PixelFormat
import org.graphiks.kadre.capture.PixelPlaneLayout
import org.graphiks.kadre.capture.ColorRange
import org.graphiks.kadre.capture.TransferFunction

/**
 * Les kernels PURS de capture iOS/tvOS (phase 9, Task 1) : les scalaires pré-extraits d'un
 * `CVPixelBuffer` ReplayKit ou d'un bitmap renderer entrent, les formes foundation sortent —
 * RIEN ici n'importe un type SDK (le pattern jeton du module, miroir [UIKitDisplayInfo]) :
 * le port de la Task suivante lit le klib (`CVPixelBufferLockBaseAddress`,
 * `CGImageGetBitmapInfo`…) et appelle ces kernels avec des `Int`/`Long`/`Boolean`, ce qui
 * rend la traduction testable dans `uikitTest` sans appareil.
 *
 * Le contrat portable est la table de `CapturePortFrame.validatePortablePlaneLayouts`
 * (runtime `CapturePort.kt:176-231`) : Rgba8/Bgra8/Bgrx8 = UN plan, `pixelStride` 4, aucun
 * sous-échantillonnage, et les planchers de `PixelPlaneLayout` (`rowStride ≥ (w-1)·4+1`,
 * `byteCount ≥ (h-1)·rowStride + (w-1)·4+1`).
 */

/** Le fourcc 'BGRA' — `kCVPixelFormatType_32BGRA` (1111970369), LA sortie ReplayKit. */
internal const val UIKIT_CVFORMAT_32BGRA = 0x42475241L

/** Le fourcc 'RGBA' — `kCVPixelFormatType_32RGBA` (1380401729). */
internal const val UIKIT_CVFORMAT_32RGBA = 0x52474241L

/** Le fourcc 'ARGB' — `kCVPixelFormatType_32ARGB` (1095911234). */
internal const val UIKIT_CVFORMAT_32ARGB = 0x41524742L

/** Le fourcc 'ABGR' — `kCVPixelFormatType_32ABGR` (1094862674). */
internal const val UIKIT_CVFORMAT_32ABGR = 0x41424752L

/** Le fourcc '420v' — `kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange` (875704438). */
internal const val UIKIT_CVFORMAT_420VIDEO = 0x34323076L

/** Le fourcc '420f' — `kCVPixelFormatType_420YpCbCr8BiPlanarFullRange` (875704422). */
internal const val UIKIT_CVFORMAT_420FULL = 0x34323066L

/**
 * La table de layout portable d'un pixel buffer packed 32bpp — la PAIRE
 * (`PixelFormat`, `PixelPlaneLayout`) que la couture porte à `CapturePortPlane`, ou `null`
 * quand la combinaison n'est PAS la table (jamais un layout approximé).
 *
 * L'honnêteté `bytesPerRow` : un stride paddé (≥ 4·w, padding non aligné pixel INCLUS) est
 * LÉGAL — le layout le porte verbatim en `rowStride` et compte `h·rowStride` en `byteCount`
 * (le port copie des rangées complètes ; les rangées de padding font partie du buffer réel).
 * Le plancher du validateur (`rowStride ≥ (w-1)·4+1`) est VOLONTAIREMENT resserré ici en
 * `rowStride ≥ 4·w` : une rangée qui ne tient pas pixel COMPLET n'est pas un packed 32bpp.
 * Tout le reste est malhonnête : dimension non positive, rangée incomplète, `byteCount`
 * hors Int — `null`, que le port traduira en échec (miroir `uikitDisplayPortDisplay`).
 *
 * `bitmapInfoIsBgra` : la couture lit le bitmap info RÉEL (pour un `CVPixelBuffer`, le
 * fourcc ; pour un `CGImage`, `CGImageGetBitmapInfo` — alpha-first little-endian ⇒ BGRA,
 * alpha-last ⇒ RGBA) et réduit à ce booléen : le kernel NE SUPPOSE JAMAIS l'ordre des
 * octets (§ décision 3 du rapport de spike). Cette forme feuille ne voit QUE des ordres
 * d'octets déjà prouvés — la PORTE fourcc ([uikitCaptureLayoutOfFourcc]) est l'entrée SDK
 * qui n'admet que 'BGRA'/'RGBA'.
 */
internal fun uikitCaptureLayoutOf(
    width: Int,
    height: Int,
    bytesPerRow: Int,
    bitmapInfoIsBgra: Boolean,
): Pair<PixelFormat, PixelPlaneLayout>? {
    val layout = packed32LayoutOrNull(width = width, height = height, bytesPerRow = bytesPerRow)
        ?: return null
    val format = if (bitmapInfoIsBgra) PixelFormat.Bgra8 else PixelFormat.Rgba8
    return format to layout
}

/**
 * La PORTE fourcc du chemin pixel buffer — L'ENTRÉE SDK de la table de layout : seuls
 * 'BGRA' et 'RGBA' (les deux seuls ordres d'octets portables, ReplayKit documenté 32BGRA)
 * y passent. 'ARGB'/'ABGR' répondent `null` : sous la convention d'ordre des lettres
 * CoreVideo leur mémoire est `[A][R][G][B]` / `[A][B][G][R]` — AUCUN étiquetage ne les
 * rend Rgba8 (ce serait un swap de canaux), ils ne sont pas mesurés (revue Important-2) :
 * `null`, jamais un format inventé. Les codes hors table (planaire, inconnu) aussi.
 */
internal fun uikitCaptureLayoutOfFourcc(
    width: Int,
    height: Int,
    bytesPerRow: Int,
    cvFormat: Long,
): Pair<PixelFormat, PixelPlaneLayout>? = when (cvFormat) {
    UIKIT_CVFORMAT_32BGRA -> uikitCaptureLayoutOf(width, height, bytesPerRow, bitmapInfoIsBgra = true)
    UIKIT_CVFORMAT_32RGBA -> uikitCaptureLayoutOf(width, height, bytesPerRow, bitmapInfoIsBgra = false)
    else -> null
}

/**
 * L'encodage couleur d'un pixel buffer depuis SON SEUL code fourcc — la mapping épinglée
 * par la spike (§ décision 2 du rapport) :
 *
 * - **Packed 8-bit RGB** ('BGRA', 'RGBA', 'ARGB', 'ABGR') :
 *   `ColorEncoding(Bt709, Srgb, Identity, Full, None)`. Justification — le transfert est
 *   sRGB (le contenu d'écran est composé en sRGB) ; les chromaticités PRIMAIRES sRGB sont
 *   NUMÉRIQUEMENT celles du BT.709 (ITU-R BT.715 : R 0.640/0.330, G 0.300/0.600,
 *   B 0.150/0.060, D65) — `Bt709` est donc la valeur exacte, pas une approximation ;
 *   des données RGB ne portent AUCUNE matrice YCbCr ⇒ `Identity` ; du 8-bit RGB est
 *   full-range (0-255) par définition.
 * - **Bi-planaire YCbCr** : le code fourcc NE PORTE que le range — le suffixe 'v'/'f' EST
 *   le fait (VideoRange = limité 16-235, FullRange = 0-255) ; primaires, transfert et
 *   matrice dépendent des tags du buffer (601 vs 709), PAS du code ⇒ `Unknown` honnête.
 * - **Tout autre code** : `Unknown` partout — un code hors table n'autorise rien.
 */
internal fun uikitCaptureColorEncodingOf(cvFormat: Long): ColorEncoding = when (cvFormat) {
    UIKIT_CVFORMAT_32BGRA, UIKIT_CVFORMAT_32RGBA, UIKIT_CVFORMAT_32ARGB, UIKIT_CVFORMAT_32ABGR -> ColorEncoding(
        primaries = ColorPrimaries.Bt709,
        transfer = TransferFunction.Srgb,
        matrix = MatrixCoefficients.Identity,
        range = ColorRange.Full,
        hdr = HdrMetadata.None,
    )

    UIKIT_CVFORMAT_420VIDEO -> ColorEncoding(
        primaries = ColorPrimaries.Unknown,
        transfer = TransferFunction.Unknown,
        matrix = MatrixCoefficients.Unknown,
        range = ColorRange.Limited,
        hdr = HdrMetadata.Unknown,
    )

    UIKIT_CVFORMAT_420FULL -> ColorEncoding(
        primaries = ColorPrimaries.Unknown,
        transfer = TransferFunction.Unknown,
        matrix = MatrixCoefficients.Unknown,
        range = ColorRange.Full,
        hdr = HdrMetadata.Unknown,
    )

    else -> ColorEncoding(
        primaries = ColorPrimaries.Unknown,
        transfer = TransferFunction.Unknown,
        matrix = MatrixCoefficients.Unknown,
        range = ColorRange.Unknown,
        hdr = HdrMetadata.Unknown,
    )
}

/**
 * Les faits bruts d'UN bitmap renderer (la pompe surface du ruling : `drawViewHierarchy`
 * + `UIGraphicsImageRenderer`) — les scalaires pré-extraits que la couture lit du
 * `CGImage` produit (`CGImageGetWidth/GetHeight/GetBytesPerRow/GetBitmapInfo`).
 *
 * `format` porte la vérité LUE du bitmap info (jamais supposée, § décision 3 du rapport) :
 * alpha-premultiplied-first + byteOrder32Little ⇒ `Bgra8`, alpha-last ⇒ `Rgba8`,
 * `noneSkip*` sans alpha ⇒ `Bgrx8` — TOUTE AUTRE lecture ne doit PAS arriver ici (la
 * couture passe un format portable ou n'appelle pas).
 */
internal data class UIKitSurfaceFrameFacts(
    val width: Int,
    val height: Int,
    val bytesPerRow: Int,
    val format: PixelFormat,
)

/**
 * Le layout portable d'UN bitmap renderer depuis ses faits — `null` quand les faits sont
 * malhonnêtes : format NON packed (un bitmap renderer est TOUJOURS 32bpp packed — un fait
 * planaire ou opaque est un mensonge de couture), dimension non positive, rangée
 * incomplète, `byteCount` hors Int. Mêmes règles que [uikitCaptureLayoutOf] (même table
 * portable), moins la décision BGRA/RGBA — déjà portée par [UIKitSurfaceFrameFacts.format].
 */
internal fun uikitSurfaceLayoutOf(facts: UIKitSurfaceFrameFacts): PixelPlaneLayout? {
    val packed = facts.format is PixelFormat.Rgba8 ||
        facts.format is PixelFormat.Bgra8 ||
        facts.format is PixelFormat.Bgrx8
    if (!packed) return null
    return packed32LayoutOrNull(width = facts.width, height = facts.height, bytesPerRow = facts.bytesPerRow)
}

/**
 * Les règles d'honnêteté COMMUNES des deux chemins (packed 32bpp) : dimension positive,
 * rangée complète (`bytesPerRow ≥ 4·w`), `byteCount = h·bytesPerRow` dans Int. Un seul
 * endroit — les deux kernels ne peuvent pas diverger sur ce qui est portable.
 */
private fun packed32LayoutOrNull(width: Int, height: Int, bytesPerRow: Int): PixelPlaneLayout? {
    if (width <= 0 || height <= 0) return null
    if (bytesPerRow <= 0) return null
    if (bytesPerRow.toLong() < width.toLong() * 4L) return null
    val byteCount = height.toLong() * bytesPerRow.toLong()
    if (byteCount > Int.MAX_VALUE.toLong()) return null
    return PixelPlaneLayout(
        width = width,
        height = height,
        rowStride = bytesPerRow,
        pixelStride = 4,
        byteCount = byteCount.toInt(),
        horizontalSubsampling = 1,
        verticalSubsampling = 1,
    )
}

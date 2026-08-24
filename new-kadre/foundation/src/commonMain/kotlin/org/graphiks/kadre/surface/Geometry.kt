package org.graphiks.kadre.surface

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.truncate

public class LogicalPoint(x: Double, y: Double) {
    public val x: Double = finiteCanonical(x, "x")
    public val y: Double = finiteCanonical(y, "y")

    public operator fun component1(): Double = x
    public operator fun component2(): Double = y
    public fun copy(x: Double = this.x, y: Double = this.y): LogicalPoint = LogicalPoint(x, y)
    override fun equals(other: Any?): Boolean = other is LogicalPoint && x == other.x && y == other.y
    override fun hashCode(): Int = 31 * x.hashCode() + y.hashCode()
    override fun toString(): String = "LogicalPoint(x=$x, y=$y)"
}

public class LogicalDelta(x: Double, y: Double) {
    public val x: Double = finiteCanonical(x, "x")
    public val y: Double = finiteCanonical(y, "y")

    public operator fun component1(): Double = x
    public operator fun component2(): Double = y
    public fun copy(x: Double = this.x, y: Double = this.y): LogicalDelta = LogicalDelta(x, y)
    override fun equals(other: Any?): Boolean = other is LogicalDelta && x == other.x && y == other.y
    override fun hashCode(): Int = 31 * x.hashCode() + y.hashCode()
    override fun toString(): String = "LogicalDelta(x=$x, y=$y)"
}

public class LogicalSize(width: Double, height: Double) {
    public val width: Double = finiteCanonical(width, "width").also {
        require(it > 0.0) { "width must be positive" }
    }
    public val height: Double = finiteCanonical(height, "height").also {
        require(it > 0.0) { "height must be positive" }
    }

    public operator fun component1(): Double = width
    public operator fun component2(): Double = height
    public fun copy(width: Double = this.width, height: Double = this.height): LogicalSize =
        LogicalSize(width, height)
    override fun equals(other: Any?): Boolean = other is LogicalSize && width == other.width && height == other.height
    override fun hashCode(): Int = 31 * width.hashCode() + height.hashCode()
    override fun toString(): String = "LogicalSize(width=$width, height=$height)"
}

public data class LogicalRect(public val origin: LogicalPoint, public val size: LogicalSize)

public class LogicalInsets(top: Double, right: Double, bottom: Double, left: Double) {
    public val top: Double = nonNegative(top, "top")
    public val right: Double = nonNegative(right, "right")
    public val bottom: Double = nonNegative(bottom, "bottom")
    public val left: Double = nonNegative(left, "left")

    public operator fun component1(): Double = top
    public operator fun component2(): Double = right
    public operator fun component3(): Double = bottom
    public operator fun component4(): Double = left
    public fun copy(
        top: Double = this.top,
        right: Double = this.right,
        bottom: Double = this.bottom,
        left: Double = this.left,
    ): LogicalInsets = LogicalInsets(top, right, bottom, left)
    override fun equals(other: Any?): Boolean =
        other is LogicalInsets && top == other.top && right == other.right && bottom == other.bottom && left == other.left
    override fun hashCode(): Int = 31 * (31 * (31 * top.hashCode() + right.hashCode()) + bottom.hashCode()) + left.hashCode()
    override fun toString(): String = "LogicalInsets(top=$top, right=$right, bottom=$bottom, left=$left)"
}

public data class PhysicalPoint(public val x: Int, public val y: Int)

public data class PhysicalSize(public val width: Int, public val height: Int) {
    init {
        require(width > 0 && height > 0) { "physical dimensions must be positive" }
    }
}

public data class PhysicalRect(public val origin: PhysicalPoint, public val size: PhysicalSize)

public enum class PixelRounding { Floor, Ceil, NearestTiesToEven, TowardZero }

public fun LogicalPoint.toPhysical(
    scaleFactor: Double,
    rounding: PixelRounding = PixelRounding.NearestTiesToEven,
): PhysicalPoint {
    validateScale(scaleFactor)
    return PhysicalPoint(
        checkedRound(x * scaleFactor, rounding),
        checkedRound(y * scaleFactor, rounding),
    )
}

public fun LogicalSize.toPhysical(
    scaleFactor: Double,
    rounding: PixelRounding = PixelRounding.Ceil,
): PhysicalSize {
    validateScale(scaleFactor)
    return PhysicalSize(
        checkedRound(width * scaleFactor, rounding),
        checkedRound(height * scaleFactor, rounding),
    )
}

public fun PhysicalPoint.toLogical(scaleFactor: Double): LogicalPoint {
    validateScale(scaleFactor)
    return LogicalPoint(x / scaleFactor, y / scaleFactor)
}

public fun PhysicalSize.toLogical(scaleFactor: Double): LogicalSize {
    validateScale(scaleFactor)
    return LogicalSize(width / scaleFactor, height / scaleFactor)
}

public sealed interface PropertyChange<out T> {
    public data object Unchanged : PropertyChange<Nothing>
    public data class Set<T>(public val value: T) : PropertyChange<T>
    public data object Clear : PropertyChange<Nothing>
}

public enum class ImageFormat { Png, Jpeg, Webp, Rgba8 }

public data class ImageConstraints(
    public val maximumSize: PhysicalSize,
    public val formats: Set<ImageFormat>,
) {
    init {
        require(formats.isNotEmpty()) { "formats must not be empty" }
    }
}

public class BinaryImage(
    bytes: ByteArray,
    public val format: ImageFormat,
    public val pixelSize: PhysicalSize? = null,
) {
    private val ownedBytes: ByteArray = bytes.copyOf()

    public val bytes: ByteArray
        get() = ownedBytes.copyOf()

    init {
        if (format == ImageFormat.Rgba8) {
            requireNotNull(pixelSize) { "Rgba8 requires pixelSize" }
            val pixels = pixelSize.width.toLong() * pixelSize.height.toLong()
            val requiredBytes = pixels * 4L
            require(requiredBytes <= Int.MAX_VALUE && requiredBytes == ownedBytes.size.toLong()) {
                "Rgba8 byte count does not match pixelSize"
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is BinaryImage && format == other.format && pixelSize == other.pixelSize && ownedBytes.contentEquals(other.ownedBytes)

    override fun hashCode(): Int = 31 * (31 * format.hashCode() + (pixelSize?.hashCode() ?: 0)) + ownedBytes.contentHashCode()

    override fun toString(): String = "BinaryImage(format=$format, pixelSize=$pixelSize, byteCount=${ownedBytes.size})"
}

public data class CursorImage(public val image: BinaryImage, public val hotspot: PhysicalPoint) {
    init {
        val size = requireNotNull(image.pixelSize) { "cursor image requires pixelSize" }
        require(hotspot.x >= 0 && hotspot.y >= 0 && hotspot.x < size.width && hotspot.y < size.height) {
            "cursor hotspot must be inside the image"
        }
    }
}

private fun finiteCanonical(value: Double, name: String): Double {
    require(value.isFinite()) { "$name must be finite" }
    return if (value == 0.0) 0.0 else value
}

private fun nonNegative(value: Double, name: String): Double = finiteCanonical(value, name).also {
    require(it >= 0.0) { "$name must be non-negative" }
}

private fun validateScale(scaleFactor: Double) {
    require(scaleFactor.isFinite() && scaleFactor > 0.0) { "scaleFactor must be finite and positive" }
}

private fun checkedRound(value: Double, rounding: PixelRounding): Int {
    require(value.isFinite()) { "physical coordinate is not finite" }
    val rounded = when (rounding) {
        PixelRounding.Floor -> floor(value)
        PixelRounding.Ceil -> ceil(value)
        PixelRounding.NearestTiesToEven -> round(value)
        PixelRounding.TowardZero -> truncate(value)
    }
    require(rounded >= Int.MIN_VALUE.toDouble() && rounded <= Int.MAX_VALUE.toDouble()) {
        "physical coordinate does not fit Int"
    }
    return rounded.toInt()
}

package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSBitmapImageRep
 * Superclass: NSImageRep
 * Protocols: NSSecureCoding
 */
open class NSBitmapImageRep(ptr: MemorySegment) : NSImageRep(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBitmapImageRep") }
        
        /** @return NSArray<NSImageRep *> * */
        fun imageRepsWithData(`data`: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepsWithData:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`) as MemorySegment
        }
        
        fun imageRepWithData(`data`: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepWithData:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`) as MemorySegment
        }
        
        fun TIFFRepresentationOfImageRepsInArray(array: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("TIFFRepresentationOfImageRepsInArray:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, array) as MemorySegment
        }
        
        fun TIFFRepresentationOfImageRepsInArray_usingCompression_factor(array: MemorySegment, comp: NSTIFFCompression, factor: Float): MemorySegment {
            val sel = ObjCRuntime.sel("TIFFRepresentationOfImageRepsInArray:usingCompression:factor:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, array, comp, factor) as MemorySegment
        }
        
        fun getTIFFCompressionTypes_count(list: MemorySegment, numTypes: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("getTIFFCompressionTypes:count:")
            ObjCRuntime.msgSend(null, _class, sel, list, numTypes)
        }
        
        fun localizedNameForTIFFCompressionType(compression: NSTIFFCompression): MemorySegment {
            val sel = ObjCRuntime.sel("localizedNameForTIFFCompressionType:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, compression) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun localizedNameForTIFFCompressionTypeAsString(compression: NSTIFFCompression): String = ObjCRuntime.toJavaString(localizedNameForTIFFCompressionType(compression))
        
    }
    
    fun initWithFocusedViewRect(rect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFocusedViewRect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initWithBitmapDataPlanes_pixelsWide_pixelsHigh_bitsPerSample_samplesPerPixel_hasAlpha_isPlanar_colorSpaceName_bytesPerRow_bitsPerPixel(planes: MemorySegment, width: NSInteger, height: NSInteger, bps: NSInteger, spp: NSInteger, alpha: BOOL, isPlanar: BOOL, colorSpaceName: NSColorSpaceName, rBytes: NSInteger, pBits: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithBitmapDataPlanes:pixelsWide:pixelsHigh:bitsPerSample:samplesPerPixel:hasAlpha:isPlanar:colorSpaceName:bytesPerRow:bitsPerPixel:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, planes, width, height, bps, spp, alpha, isPlanar, colorSpaceName, rBytes, pBits) as MemorySegment
    }
    
    fun initWithBitmapDataPlanes_pixelsWide_pixelsHigh_bitsPerSample_samplesPerPixel_hasAlpha_isPlanar_colorSpaceName_bitmapFormat_bytesPerRow_bitsPerPixel(planes: MemorySegment, width: NSInteger, height: NSInteger, bps: NSInteger, spp: NSInteger, alpha: BOOL, isPlanar: BOOL, colorSpaceName: NSColorSpaceName, bitmapFormat: NSBitmapFormat, rBytes: NSInteger, pBits: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithBitmapDataPlanes:pixelsWide:pixelsHigh:bitsPerSample:samplesPerPixel:hasAlpha:isPlanar:colorSpaceName:bitmapFormat:bytesPerRow:bitsPerPixel:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, planes, width, height, bps, spp, alpha, isPlanar, colorSpaceName, bitmapFormat, rBytes, pBits) as MemorySegment
    }
    
    fun initWithCGImage(cgImage: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGImage:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, cgImage) as MemorySegment
    }
    
    fun initWithCIImage(ciImage: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCIImage:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ciImage) as MemorySegment
    }
    
    fun initWithData(`data`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`) as MemorySegment
    }
    
    fun getBitmapDataPlanes(`data`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getBitmapDataPlanes:")
        ObjCRuntime.msgSend(null, ptr, sel, `data`)
    }
    
    fun getCompression_factor(compression: MemorySegment, factor: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getCompression:factor:")
        ObjCRuntime.msgSend(null, ptr, sel, compression, factor)
    }
    
    fun setCompression_factor(compression: NSTIFFCompression, factor: Float): Unit {
        val sel = ObjCRuntime.sel("setCompression:factor:")
        ObjCRuntime.msgSend(null, ptr, sel, compression, factor)
    }
    
    fun TIFFRepresentationUsingCompression_factor(comp: NSTIFFCompression, factor: Float): MemorySegment {
        val sel = ObjCRuntime.sel("TIFFRepresentationUsingCompression:factor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, comp, factor) as MemorySegment
    }
    
    fun canBeCompressedUsing(compression: NSTIFFCompression): BOOL {
        val sel = ObjCRuntime.sel("canBeCompressedUsing:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, compression) as BOOL
    }
    
    fun colorizeByMappingGray_toColor_blackMapping_whiteMapping(midPoint: CGFloat, midPointColor: MemorySegment, shadowColor: MemorySegment, lightColor: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("colorizeByMappingGray:toColor:blackMapping:whiteMapping:")
        ObjCRuntime.msgSend(null, ptr, sel, midPoint, midPointColor, shadowColor, lightColor)
    }
    
    fun initForIncrementalLoad(): MemorySegment {
        val sel = ObjCRuntime.sel("initForIncrementalLoad")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun incrementalLoadFromData_complete(`data`: MemorySegment, complete: BOOL): NSInteger {
        val sel = ObjCRuntime.sel("incrementalLoadFromData:complete:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, `data`, complete) as NSInteger
    }
    
    fun setColor_atX_y(color: MemorySegment, x: NSInteger, y: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setColor:atX:y:")
        ObjCRuntime.msgSend(null, ptr, sel, color, x, y)
    }
    
    fun colorAtX_y(x: NSInteger, y: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("colorAtX:y:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, x, y) as MemorySegment
    }
    
    fun getPixel_atX_y(p: MemorySegment, x: NSInteger, y: NSInteger): Unit {
        val sel = ObjCRuntime.sel("getPixel:atX:y:")
        ObjCRuntime.msgSend(null, ptr, sel, p, x, y)
    }
    
    fun setPixel_atX_y(p: MemorySegment, x: NSInteger, y: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setPixel:atX:y:")
        ObjCRuntime.msgSend(null, ptr, sel, p, x, y)
    }
    
    fun bitmapImageRepByConvertingToColorSpace_renderingIntent(targetSpace: MemorySegment, renderingIntent: NSColorRenderingIntent): MemorySegment {
        val sel = ObjCRuntime.sel("bitmapImageRepByConvertingToColorSpace:renderingIntent:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, targetSpace, renderingIntent) as MemorySegment
    }
    
    fun bitmapImageRepByRetaggingWithColorSpace(newSpace: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("bitmapImageRepByRetaggingWithColorSpace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, newSpace) as MemorySegment
    }
    
    // @property bitmapData
    fun bitmapData(): MemorySegment {
        val sel = ObjCRuntime.sel("bitmapData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property planar
    fun isPlanar(): BOOL {
        val sel = ObjCRuntime.sel("isPlanar")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property samplesPerPixel
    fun samplesPerPixel(): NSInteger {
        val sel = ObjCRuntime.sel("samplesPerPixel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property bitsPerPixel
    fun bitsPerPixel(): NSInteger {
        val sel = ObjCRuntime.sel("bitsPerPixel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property bytesPerRow
    fun bytesPerRow(): NSInteger {
        val sel = ObjCRuntime.sel("bytesPerRow")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property bytesPerPlane
    fun bytesPerPlane(): NSInteger {
        val sel = ObjCRuntime.sel("bytesPerPlane")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property numberOfPlanes
    fun numberOfPlanes(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfPlanes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property bitmapFormat
    fun bitmapFormat(): NSBitmapFormat {
        val sel = ObjCRuntime.sel("bitmapFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBitmapFormat
    }
    
    // @property TIFFRepresentation
    fun TIFFRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("TIFFRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property CGImage
    fun CGImage(): MemorySegment {
        val sel = ObjCRuntime.sel("CGImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property colorSpace
    fun colorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("colorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSBitmapImageFileTypeExtensions on NSBitmapImageRep ─────────────────────────────────────────

fun NSBitmapImageRep.representationUsingType_properties(storageType: NSBitmapImageFileType, properties: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("representationUsingType:properties:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, storageType, properties) as MemorySegment
}

fun NSBitmapImageRep.setProperty_withValue(property: NSBitmapImageRepPropertyKey, value: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setProperty:withValue:")
    ObjCRuntime.msgSend(null, ptr, sel, property, value)
}

fun NSBitmapImageRep.valueForProperty(property: NSBitmapImageRepPropertyKey): MemorySegment {
    val sel = ObjCRuntime.sel("valueForProperty:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, property) as MemorySegment
}

// Class<*> method: +[NSBitmapImageRep representationOfImageRepsInArray:usingType:properties:]
fun NSBitmapImageRep_representationOfImageRepsInArray_usingType_properties(imageReps: MemorySegment, storageType: NSBitmapImageFileType, properties: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("representationOfImageRepsInArray:usingType:properties:")
    val cls = ObjCRuntime.getClass("NSBitmapImageRep")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, imageReps, storageType, properties) as MemorySegment
}


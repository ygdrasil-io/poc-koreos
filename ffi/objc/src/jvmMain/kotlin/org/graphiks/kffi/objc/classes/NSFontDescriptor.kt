package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSFontDescriptor
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSFontDescriptor(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFontDescriptor") }
        
        open fun fontDescriptorWithFontAttributes(attributes: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fontDescriptorWithFontAttributes:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, attributes) as MemorySegment
        }
        
        open fun fontDescriptorWithName_size(fontName: MemorySegment, size: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("fontDescriptorWithName:size:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontName, size) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun fontDescriptorWithName_size(fontName: String, size: CGFloat): MemorySegment = fontDescriptorWithName_size(ObjCRuntime.newNSString(Arena.global(), fontName), size)
        
        open fun fontDescriptorWithName_matrix(fontName: MemorySegment, matrix: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fontDescriptorWithName:matrix:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fontName, matrix) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun fontDescriptorWithName_matrix(fontName: String, matrix: MemorySegment): MemorySegment = fontDescriptorWithName_matrix(ObjCRuntime.newNSString(Arena.global(), fontName), matrix)
        
    }
    
    open fun objectForKey(attribute: NSFontDescriptorAttributeName): MemorySegment {
        val sel = ObjCRuntime.sel("objectForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attribute) as MemorySegment
    }
    
    open fun initWithFontAttributes(attributes: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFontAttributes:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attributes) as MemorySegment
    }
    
    /** @return NSArray<NSFontDescriptor *> * */
    open fun matchingFontDescriptorsWithMandatoryKeys(mandatoryKeys: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("matchingFontDescriptorsWithMandatoryKeys:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mandatoryKeys) as MemorySegment
    }
    
    open fun matchingFontDescriptorWithMandatoryKeys(mandatoryKeys: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("matchingFontDescriptorWithMandatoryKeys:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, mandatoryKeys) as MemorySegment
    }
    
    open fun fontDescriptorByAddingAttributes(attributes: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptorByAddingAttributes:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attributes) as MemorySegment
    }
    
    open fun fontDescriptorWithSymbolicTraits(symbolicTraits: NSFontDescriptorSymbolicTraits): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptorWithSymbolicTraits:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, symbolicTraits) as MemorySegment
    }
    
    open fun fontDescriptorWithSize(newPointSize: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptorWithSize:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, newPointSize) as MemorySegment
    }
    
    open fun fontDescriptorWithMatrix(matrix: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptorWithMatrix:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, matrix) as MemorySegment
    }
    
    open fun fontDescriptorWithFace(newFace: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptorWithFace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, newFace) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun fontDescriptorWithFace(newFace: String): MemorySegment = fontDescriptorWithFace(ObjCRuntime.newNSString(Arena.global(), newFace))
    
    open fun fontDescriptorWithFamily(newFamily: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptorWithFamily:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, newFamily) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun fontDescriptorWithFamily(newFamily: String): MemorySegment = fontDescriptorWithFamily(ObjCRuntime.newNSString(Arena.global(), newFamily))
    
    open fun fontDescriptorWithDesign(design: NSFontDescriptorSystemDesign): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptorWithDesign:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, design) as MemorySegment
    }
    
    // @property postscriptName
    open fun postscriptName(): MemorySegment {
        val sel = ObjCRuntime.sel("postscriptName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun postscriptNameAsString(): String = ObjCRuntime.toJavaString(postscriptName())
    
    // @property pointSize
    open fun pointSize(): CGFloat {
        val sel = ObjCRuntime.sel("pointSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property matrix
    open fun matrix(): MemorySegment {
        val sel = ObjCRuntime.sel("matrix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property symbolicTraits
    open fun symbolicTraits(): NSFontDescriptorSymbolicTraits {
        val sel = ObjCRuntime.sel("symbolicTraits")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFontDescriptorSymbolicTraits
    }
    
    // @property requiresFontAssetRequest
    open fun requiresFontAssetRequest(): BOOL {
        val sel = ObjCRuntime.sel("requiresFontAssetRequest")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property fontAttributes
    /** @return NSDictionary<NSFontDescriptorAttributeName,id> * */
    open fun fontAttributes(): MemorySegment {
        val sel = ObjCRuntime.sel("fontAttributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSFontDescriptor_TextStyles on NSFontDescriptor ─────────────────────────────────────────

// Class<*> method: +[NSFontDescriptor preferredFontDescriptorForTextStyle:options:]
fun NSFontDescriptor_preferredFontDescriptorForTextStyle_options(style: NSFontTextStyle, options: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("preferredFontDescriptorForTextStyle:options:")
    val cls = ObjCRuntime.getClass("NSFontDescriptor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, style, options) as MemorySegment
}


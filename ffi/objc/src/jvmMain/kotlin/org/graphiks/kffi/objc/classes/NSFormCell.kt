package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSFormCell
 * Superclass: NSActionCell
 */
open class NSFormCell(ptr: MemorySegment) : NSActionCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFormCell") }
        
    }
    
    fun initTextCell(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initTextCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initTextCell(string: String): MemorySegment = initTextCell(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun initImageCell(image: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initImageCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, image) as MemorySegment
    }
    
    fun titleWidth(size: NSSize): CGFloat {
        val sel = ObjCRuntime.sel("titleWidth:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"))) as CGFloat
    }
    
    // @property titleWidth
    fun titleWidth(): CGFloat {
        val sel = ObjCRuntime.sel("titleWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setTitleWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setTitleWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property title
    fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property titleFont
    fun titleFont(): MemorySegment {
        val sel = ObjCRuntime.sel("titleFont")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitleFont(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitleFont:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property opaque
    fun isOpaque(): BOOL {
        val sel = ObjCRuntime.sel("isOpaque")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property placeholderString
    fun placeholderString(): MemorySegment {
        val sel = ObjCRuntime.sel("placeholderString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPlaceholderString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPlaceholderString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun placeholderStringAsString(): String = ObjCRuntime.toJavaString(placeholderString())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPlaceholderString(value: String) = setPlaceholderString(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property placeholderAttributedString
    fun placeholderAttributedString(): MemorySegment {
        val sel = ObjCRuntime.sel("placeholderAttributedString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPlaceholderAttributedString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPlaceholderAttributedString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property titleAlignment
    fun titleAlignment(): NSTextAlignment {
        val sel = ObjCRuntime.sel("titleAlignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
    }
    fun setTitleAlignment(value: NSTextAlignment) {
        val sel = ObjCRuntime.sel("setTitleAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property titleBaseWritingDirection
    fun titleBaseWritingDirection(): NSWritingDirection {
        val sel = ObjCRuntime.sel("titleBaseWritingDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingDirection
    }
    fun setTitleBaseWritingDirection(value: NSWritingDirection) {
        val sel = ObjCRuntime.sel("setTitleBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preferredTextFieldWidth
    fun preferredTextFieldWidth(): CGFloat {
        val sel = ObjCRuntime.sel("preferredTextFieldWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setPreferredTextFieldWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setPreferredTextFieldWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSKeyboardUI on NSFormCell ─────────────────────────────────────────

fun NSFormCell.setTitleWithMnemonic(stringWithAmpersand: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleWithMnemonic:")
    ObjCRuntime.msgSend(null, ptr, sel, stringWithAmpersand)
}

// ── Category: NSFormCellAttributedStringMethods on NSFormCell ─────────────────────────────────────────

fun NSFormCell.attributedTitle(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedTitle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSFormCell.setAttributedTitle(attributedTitle: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributedTitle:")
    ObjCRuntime.msgSend(null, ptr, sel, attributedTitle)
}

// @property attributedTitle
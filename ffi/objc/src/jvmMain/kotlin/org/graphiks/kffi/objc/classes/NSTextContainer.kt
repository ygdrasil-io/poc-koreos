package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextContainer
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSTextContainer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextContainer") }
        
    }
    
    open fun initWithSize(size: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSize:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"))) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun lineFragmentRectForProposedRect_atIndex_writingDirection_remainingRect(proposedRect: MemorySegment, characterIndex: NSUInteger, baseWritingDirection: NSWritingDirection, remainingRect: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("lineFragmentRectForProposedRect:atIndex:writingDirection:remainingRect:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(proposedRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), characterIndex, baseWritingDirection, remainingRect) as MemorySegment
    }
    
    // @property textLayoutManager
    open fun textLayoutManager(): MemorySegment {
        val sel = ObjCRuntime.sel("textLayoutManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property size
    open fun size(): MemorySegment {
        val sel = ObjCRuntime.sel("size")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as MemorySegment
    }
    open fun setSize(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property lineBreakMode
    open fun lineBreakMode(): NSLineBreakMode {
        val sel = ObjCRuntime.sel("lineBreakMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineBreakMode
    }
    open fun setLineBreakMode(value: NSLineBreakMode) {
        val sel = ObjCRuntime.sel("setLineBreakMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineFragmentPadding
    open fun lineFragmentPadding(): CGFloat {
        val sel = ObjCRuntime.sel("lineFragmentPadding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setLineFragmentPadding(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLineFragmentPadding:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumNumberOfLines
    open fun maximumNumberOfLines(): NSUInteger {
        val sel = ObjCRuntime.sel("maximumNumberOfLines")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    open fun setMaximumNumberOfLines(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMaximumNumberOfLines:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property simpleRectangularTextContainer
    open fun isSimpleRectangularTextContainer(): BOOL {
        val sel = ObjCRuntime.sel("isSimpleRectangularTextContainer")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property widthTracksTextView
    open fun widthTracksTextView(): BOOL {
        val sel = ObjCRuntime.sel("widthTracksTextView")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setWidthTracksTextView(value: BOOL) {
        val sel = ObjCRuntime.sel("setWidthTracksTextView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property heightTracksTextView
    open fun heightTracksTextView(): BOOL {
        val sel = ObjCRuntime.sel("heightTracksTextView")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setHeightTracksTextView(value: BOOL) {
        val sel = ObjCRuntime.sel("setHeightTracksTextView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category:  on NSTextContainer ─────────────────────────────────────────

fun NSTextContainer.layoutManager(): MemorySegment {
    val sel = ObjCRuntime.sel("layoutManager")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextContainer.setLayoutManager(layoutManager: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setLayoutManager:")
    ObjCRuntime.msgSend(null, ptr, sel, layoutManager)
}

fun NSTextContainer.replaceLayoutManager(newLayoutManager: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("replaceLayoutManager:")
    ObjCRuntime.msgSend(null, ptr, sel, newLayoutManager)
}

/** @return NSArray<NSBezierPath *> * */
fun NSTextContainer.exclusionPaths(): MemorySegment {
    val sel = ObjCRuntime.sel("exclusionPaths")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextContainer.setExclusionPaths(exclusionPaths: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setExclusionPaths:")
    ObjCRuntime.msgSend(null, ptr, sel, exclusionPaths)
}

fun NSTextContainer.textView(): MemorySegment {
    val sel = ObjCRuntime.sel("textView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextContainer.setTextView(textView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTextView:")
    ObjCRuntime.msgSend(null, ptr, sel, textView)
}

// @property layoutManager
/** @return NSArray<NSBezierPath *> * */
fun NSTextContainer.initWithContainerSize(aContainerSize: NSSize): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContainerSize:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aContainerSize) as MemorySegment
}

fun NSTextContainer.lineFragmentRectForProposedRect_sweepDirection_movementDirection_remainingRect(proposedRect: NSRect, sweepDirection: NSLineSweepDirection, movementDirection: NSLineMovementDirection, remainingRect: MemorySegment): NSRect {
    val sel = ObjCRuntime.sel("lineFragmentRectForProposedRect:sweepDirection:movementDirection:remainingRect:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, proposedRect, sweepDirection, movementDirection, remainingRect) as NSRect
}

fun NSTextContainer.containsPoint(point: NSPoint): BOOL {
    val sel = ObjCRuntime.sel("containsPoint:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, point) as BOOL
}

fun NSTextContainer.containerSize(): NSSize {
    val sel = ObjCRuntime.sel("containerSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

fun NSTextContainer.setContainerSize(containerSize: NSSize): Unit {
    val sel = ObjCRuntime.sel("setContainerSize:")
    ObjCRuntime.msgSend(null, ptr, sel, containerSize)
}

// @property containerSize
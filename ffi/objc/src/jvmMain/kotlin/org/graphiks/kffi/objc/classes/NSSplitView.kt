package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSplitView
 * Superclass: NSView
 */
open class NSSplitView(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSplitView") }
        
    }
    
    fun drawDividerInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawDividerInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun adjustSubviews(): Unit {
        val sel = ObjCRuntime.sel("adjustSubviews")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun isSubviewCollapsed(subview: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isSubviewCollapsed:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, subview) as BOOL
    }
    
    fun minPossiblePositionOfDividerAtIndex(dividerIndex: NSInteger): CGFloat {
        val sel = ObjCRuntime.sel("minPossiblePositionOfDividerAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, dividerIndex) as CGFloat
    }
    
    fun maxPossiblePositionOfDividerAtIndex(dividerIndex: NSInteger): CGFloat {
        val sel = ObjCRuntime.sel("maxPossiblePositionOfDividerAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, dividerIndex) as CGFloat
    }
    
    fun setPosition_ofDividerAtIndex(position: CGFloat, dividerIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setPosition:ofDividerAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, position, dividerIndex)
    }
    
    fun holdingPriorityForSubviewAtIndex(subviewIndex: NSInteger): NSLayoutPriority {
        val sel = ObjCRuntime.sel("holdingPriorityForSubviewAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, subviewIndex) as NSLayoutPriority
    }
    
    fun setHoldingPriority_forSubviewAtIndex(priority: NSLayoutPriority, subviewIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setHoldingPriority:forSubviewAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, priority, subviewIndex)
    }
    
    // @property vertical
    fun isVertical(): BOOL {
        val sel = ObjCRuntime.sel("isVertical")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setVertical(value: BOOL) {
        val sel = ObjCRuntime.sel("setVertical:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dividerStyle
    fun dividerStyle(): NSSplitViewDividerStyle {
        val sel = ObjCRuntime.sel("dividerStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSplitViewDividerStyle
    }
    fun setDividerStyle(value: NSSplitViewDividerStyle) {
        val sel = ObjCRuntime.sel("setDividerStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autosaveName
    fun autosaveName(): NSSplitViewAutosaveName {
        val sel = ObjCRuntime.sel("autosaveName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSplitViewAutosaveName
    }
    fun setAutosaveName(value: NSSplitViewAutosaveName) {
        val sel = ObjCRuntime.sel("setAutosaveName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSSplitViewDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dividerColor
    fun dividerColor(): MemorySegment {
        val sel = ObjCRuntime.sel("dividerColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property dividerThickness
    fun dividerThickness(): CGFloat {
        val sel = ObjCRuntime.sel("dividerThickness")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
}

// ── Category: NSSplitViewArrangedSubviews on NSSplitView ─────────────────────────────────────────

fun NSSplitView.addArrangedSubview(view: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addArrangedSubview:")
    ObjCRuntime.msgSend(null, ptr, sel, view)
}

fun NSSplitView.insertArrangedSubview_atIndex(view: MemorySegment, index: NSInteger): Unit {
    val sel = ObjCRuntime.sel("insertArrangedSubview:atIndex:")
    ObjCRuntime.msgSend(null, ptr, sel, view, index)
}

fun NSSplitView.removeArrangedSubview(view: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeArrangedSubview:")
    ObjCRuntime.msgSend(null, ptr, sel, view)
}

fun NSSplitView.arrangesAllSubviews(): BOOL {
    val sel = ObjCRuntime.sel("arrangesAllSubviews")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSSplitView.setArrangesAllSubviews(arrangesAllSubviews: BOOL): Unit {
    val sel = ObjCRuntime.sel("setArrangesAllSubviews:")
    ObjCRuntime.msgSend(null, ptr, sel, arrangesAllSubviews)
}

/** @return NSArray<__kindof NSView *> * */
fun NSSplitView.arrangedSubviews(): MemorySegment {
    val sel = ObjCRuntime.sel("arrangedSubviews")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property arrangesAllSubviews
    val sel = ObjCRuntime.sel("arrangesAllSubviews")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setArrangesAllSubviews:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property arrangedSubviews
/** @return NSArray<__kindof NSView *> * */
    val sel = ObjCRuntime.sel("arrangedSubviews")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSDeprecated on NSSplitView ─────────────────────────────────────────

fun NSSplitView.setIsPaneSplitter(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setIsPaneSplitter:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSSplitView.isPaneSplitter(): BOOL {
    val sel = ObjCRuntime.sel("isPaneSplitter")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}


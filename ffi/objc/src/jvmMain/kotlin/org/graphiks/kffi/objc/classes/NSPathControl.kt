package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPathControl
 * Superclass: NSControl
 */
open class NSPathControl(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPathControl") }
        
    }
    
    fun setDraggingSourceOperationMask_forLocal(mask: NSDragOperation, isLocal: BOOL): Unit {
        val sel = ObjCRuntime.sel("setDraggingSourceOperationMask:forLocal:")
        ObjCRuntime.msgSend(null, ptr, sel, mask, isLocal)
    }
    
    // @property editable
    fun isEditable(): BOOL {
        val sel = ObjCRuntime.sel("isEditable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEditable(value: BOOL) {
        val sel = ObjCRuntime.sel("setEditable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowedTypes
    /** @return NSArray<NSString *> * */
    fun allowedTypes(): MemorySegment {
        val sel = ObjCRuntime.sel("allowedTypes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAllowedTypes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAllowedTypes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property URL
    fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property doubleAction
    fun doubleAction(): MemorySegment {
        val sel = ObjCRuntime.sel("doubleAction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDoubleAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDoubleAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pathStyle
    fun pathStyle(): NSPathStyle {
        val sel = ObjCRuntime.sel("pathStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPathStyle
    }
    fun setPathStyle(value: NSPathStyle) {
        val sel = ObjCRuntime.sel("setPathStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property clickedPathItem
    fun clickedPathItem(): MemorySegment {
        val sel = ObjCRuntime.sel("clickedPathItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property pathItems
    /** @return NSArray<NSPathControlItem *> * */
    fun pathItems(): MemorySegment {
        val sel = ObjCRuntime.sel("pathItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPathItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPathItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSPathControlDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property menu
    fun menu(): MemorySegment {
        val sel = ObjCRuntime.sel("menu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSPathControl ─────────────────────────────────────────

fun NSPathControl.clickedPathComponentCell(): MemorySegment {
    val sel = ObjCRuntime.sel("clickedPathComponentCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSPathComponentCell *> * */
fun NSPathControl.pathComponentCells(): MemorySegment {
    val sel = ObjCRuntime.sel("pathComponentCells")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSPathControl.setPathComponentCells(cells: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setPathComponentCells:")
    ObjCRuntime.msgSend(null, ptr, sel, cells)
}


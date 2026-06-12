package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPageLayout
 * Superclass: NSObject
 */
open class NSPageLayout(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPageLayout") }
        
        open fun pageLayout(): MemorySegment {
            val sel = ObjCRuntime.sel("pageLayout")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun addAccessoryController(accessoryController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addAccessoryController:")
        ObjCRuntime.msgSend(null, ptr, sel, accessoryController)
    }
    
    open fun removeAccessoryController(accessoryController: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeAccessoryController:")
        ObjCRuntime.msgSend(null, ptr, sel, accessoryController)
    }
    
    open fun beginSheetUsingPrintInfo_onWindow_completionHandler(printInfo: MemorySegment, parentWindow: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginSheetUsingPrintInfo:onWindow:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, printInfo, parentWindow, handler)
    }
    
    open fun beginSheetWithPrintInfo_modalForWindow_delegate_didEndSelector_contextInfo(printInfo: MemorySegment, docWindow: MemorySegment, delegate: MemorySegment, didEndSelector: MemorySegment, contextInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginSheetWithPrintInfo:modalForWindow:delegate:didEndSelector:contextInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, printInfo, docWindow, delegate, didEndSelector, contextInfo)
    }
    
    open fun runModalWithPrintInfo(printInfo: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("runModalWithPrintInfo:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, printInfo) as NSInteger
    }
    
    open fun runModal(): NSInteger {
        val sel = ObjCRuntime.sel("runModal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property accessoryControllers
    /** @return NSArray<__kindof NSViewController *> * */
    open fun accessoryControllers(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryControllers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property printInfo
    open fun printInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("printInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSDeprecated on NSPageLayout ─────────────────────────────────────────

fun NSPageLayout.setAccessoryView(accessoryView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAccessoryView:")
    ObjCRuntime.msgSend(null, ptr, sel, accessoryView)
}

fun NSPageLayout.accessoryView(): MemorySegment {
    val sel = ObjCRuntime.sel("accessoryView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSPageLayout.readPrintInfo(): Unit {
    val sel = ObjCRuntime.sel("readPrintInfo")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSPageLayout.writePrintInfo(): Unit {
    val sel = ObjCRuntime.sel("writePrintInfo")
    ObjCRuntime.msgSend(null, ptr, sel)
}


package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSWindowController
 * Superclass: NSResponder
 * Protocols: NSSeguePerforming
 */
open class NSWindowController(ptr: MemorySegment) : NSResponder(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWindowController") }
        
    }
    
    fun initWithWindow(window: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithWindow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, window) as MemorySegment
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun initWithWindowNibName(windowNibName: NSNibName): MemorySegment {
        val sel = ObjCRuntime.sel("initWithWindowNibName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, windowNibName) as MemorySegment
    }
    
    fun initWithWindowNibName_owner(windowNibName: NSNibName, owner: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithWindowNibName:owner:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, windowNibName, owner) as MemorySegment
    }
    
    fun initWithWindowNibPath_owner(windowNibPath: MemorySegment, owner: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithWindowNibPath:owner:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, windowNibPath, owner) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithWindowNibPath_owner(windowNibPath: String, owner: MemorySegment): MemorySegment = initWithWindowNibPath_owner(ObjCRuntime.newNSString(Arena.global(), windowNibPath), owner)
    
    fun setDocumentEdited(dirtyFlag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setDocumentEdited:")
        ObjCRuntime.msgSend(null, ptr, sel, dirtyFlag)
    }
    
    fun synchronizeWindowTitleWithDocumentName(): Unit {
        val sel = ObjCRuntime.sel("synchronizeWindowTitleWithDocumentName")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun windowTitleForDocumentDisplayName(displayName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("windowTitleForDocumentDisplayName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, displayName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun windowTitleForDocumentDisplayNameAsString(displayName: MemorySegment): String = ObjCRuntime.toJavaString(windowTitleForDocumentDisplayName(displayName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun windowTitleForDocumentDisplayName(displayName: String): MemorySegment = windowTitleForDocumentDisplayName(ObjCRuntime.newNSString(Arena.global(), displayName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun windowTitleForDocumentDisplayNameAsString(displayName: String): String = ObjCRuntime.toJavaString(windowTitleForDocumentDisplayName(ObjCRuntime.newNSString(Arena.global(), displayName)))
    
    fun windowWillLoad(): Unit {
        val sel = ObjCRuntime.sel("windowWillLoad")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun windowDidLoad(): Unit {
        val sel = ObjCRuntime.sel("windowDidLoad")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun loadWindow(): Unit {
        val sel = ObjCRuntime.sel("loadWindow")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun close(): Unit {
        val sel = ObjCRuntime.sel("close")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun showWindow(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property windowNibName
    fun windowNibName(): NSNibName {
        val sel = ObjCRuntime.sel("windowNibName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSNibName
    }
    
    // @property windowNibPath
    fun windowNibPath(): MemorySegment {
        val sel = ObjCRuntime.sel("windowNibPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun windowNibPathAsString(): String = ObjCRuntime.toJavaString(windowNibPath())
    
    // @property owner
    fun owner(): MemorySegment {
        val sel = ObjCRuntime.sel("owner")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property windowFrameAutosaveName
    fun windowFrameAutosaveName(): NSWindowFrameAutosaveName {
        val sel = ObjCRuntime.sel("windowFrameAutosaveName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWindowFrameAutosaveName
    }
    fun setWindowFrameAutosaveName(value: NSWindowFrameAutosaveName) {
        val sel = ObjCRuntime.sel("setWindowFrameAutosaveName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldCascadeWindows
    fun shouldCascadeWindows(): BOOL {
        val sel = ObjCRuntime.sel("shouldCascadeWindows")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShouldCascadeWindows(value: BOOL) {
        val sel = ObjCRuntime.sel("setShouldCascadeWindows:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property previewRepresentableActivityItems
    /** @return NSArray<id<NSPreviewRepresentableActivityItem>> * */
    fun previewRepresentableActivityItems(): MemorySegment {
        val sel = ObjCRuntime.sel("previewRepresentableActivityItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPreviewRepresentableActivityItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPreviewRepresentableActivityItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property document
    fun document(): MemorySegment {
        val sel = ObjCRuntime.sel("document")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDocument(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDocument:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldCloseDocument
    fun shouldCloseDocument(): BOOL {
        val sel = ObjCRuntime.sel("shouldCloseDocument")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShouldCloseDocument(value: BOOL) {
        val sel = ObjCRuntime.sel("setShouldCloseDocument:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property contentViewController
    fun contentViewController(): MemorySegment {
        val sel = ObjCRuntime.sel("contentViewController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContentViewController(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContentViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property window
    fun window(): MemorySegment {
        val sel = ObjCRuntime.sel("window")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setWindow(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setWindow:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property windowLoaded
    fun isWindowLoaded(): BOOL {
        val sel = ObjCRuntime.sel("isWindowLoaded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSWindowControllerStoryboardingMethods on NSWindowController ─────────────────────────────────────────

fun NSWindowController.storyboard(): MemorySegment {
    val sel = ObjCRuntime.sel("storyboard")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property storyboard
fun NSWindowController.dismissController(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("dismissController:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}


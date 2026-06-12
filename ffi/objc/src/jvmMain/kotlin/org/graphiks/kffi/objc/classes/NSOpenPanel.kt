package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSOpenPanel
 * Superclass: NSSavePanel
 */
open class NSOpenPanel(ptr: MemorySegment) : NSSavePanel(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOpenPanel") }
        
        fun openPanel(): MemorySegment {
            val sel = ObjCRuntime.sel("openPanel")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property URLs
    /** @return NSArray<NSURL *> * */
    fun URLs(): MemorySegment {
        val sel = ObjCRuntime.sel("URLs")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property resolvesAliases
    fun resolvesAliases(): BOOL {
        val sel = ObjCRuntime.sel("resolvesAliases")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setResolvesAliases(value: BOOL) {
        val sel = ObjCRuntime.sel("setResolvesAliases:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canChooseDirectories
    fun canChooseDirectories(): BOOL {
        val sel = ObjCRuntime.sel("canChooseDirectories")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanChooseDirectories(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanChooseDirectories:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsMultipleSelection
    fun allowsMultipleSelection(): BOOL {
        val sel = ObjCRuntime.sel("allowsMultipleSelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsMultipleSelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsMultipleSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canChooseFiles
    fun canChooseFiles(): BOOL {
        val sel = ObjCRuntime.sel("canChooseFiles")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanChooseFiles(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanChooseFiles:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canResolveUbiquitousConflicts
    fun canResolveUbiquitousConflicts(): BOOL {
        val sel = ObjCRuntime.sel("canResolveUbiquitousConflicts")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanResolveUbiquitousConflicts(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanResolveUbiquitousConflicts:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canDownloadUbiquitousContents
    fun canDownloadUbiquitousContents(): BOOL {
        val sel = ObjCRuntime.sel("canDownloadUbiquitousContents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanDownloadUbiquitousContents(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanDownloadUbiquitousContents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property accessoryViewDisclosed
    fun isAccessoryViewDisclosed(): BOOL {
        val sel = ObjCRuntime.sel("isAccessoryViewDisclosed")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAccessoryViewDisclosed(value: BOOL) {
        val sel = ObjCRuntime.sel("setAccessoryViewDisclosed:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: InheritedAndUnavailable on NSOpenPanel ─────────────────────────────────────────

fun NSOpenPanel.showsContentTypes(): BOOL {
    val sel = ObjCRuntime.sel("showsContentTypes")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSOpenPanel.setShowsContentTypes(showsContentTypes: BOOL): Unit {
    val sel = ObjCRuntime.sel("setShowsContentTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, showsContentTypes)
}

// @property showsContentTypes
fun NSOpenPanel.filenames(): MemorySegment {
    val sel = ObjCRuntime.sel("filenames")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSOpenPanel.beginSheetForDirectory_file_types_modalForWindow_modalDelegate_didEndSelector_contextInfo(path: MemorySegment, name: MemorySegment, fileTypes: MemorySegment, docWindow: MemorySegment, delegate: MemorySegment, didEndSelector: MemorySegment, contextInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("beginSheetForDirectory:file:types:modalForWindow:modalDelegate:didEndSelector:contextInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, path, name, fileTypes, docWindow, delegate, didEndSelector, contextInfo)
}

fun NSOpenPanel.beginForDirectory_file_types_modelessDelegate_didEndSelector_contextInfo(path: MemorySegment, name: MemorySegment, fileTypes: MemorySegment, delegate: MemorySegment, didEndSelector: MemorySegment, contextInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("beginForDirectory:file:types:modelessDelegate:didEndSelector:contextInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, path, name, fileTypes, delegate, didEndSelector, contextInfo)
}

fun NSOpenPanel.runModalForDirectory_file_types(path: MemorySegment, name: MemorySegment, fileTypes: MemorySegment): NSInteger {
    val sel = ObjCRuntime.sel("runModalForDirectory:file:types:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, path, name, fileTypes) as NSInteger
}

fun NSOpenPanel.runModalForTypes(fileTypes: MemorySegment): NSInteger {
    val sel = ObjCRuntime.sel("runModalForTypes:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, fileTypes) as NSInteger
}


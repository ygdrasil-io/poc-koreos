package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSavePanel
 * Superclass: NSPanel
 */
open class NSSavePanel(ptr: MemorySegment) : NSPanel(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSavePanel") }
        
        fun savePanel(): MemorySegment {
            val sel = ObjCRuntime.sel("savePanel")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun validateVisibleColumns(): Unit {
        val sel = ObjCRuntime.sel("validateVisibleColumns")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun ok(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("ok:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun cancel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("cancel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun beginSheetModalForWindow_completionHandler(window: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginSheetModalForWindow:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, window, handler)
    }
    
    fun beginWithCompletionHandler(handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, handler)
    }
    
    fun runModal(): NSModalResponse {
        val sel = ObjCRuntime.sel("runModal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSModalResponse
    }
    
    // @property URL
    fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property identifier
    fun identifier(): NSUserInterfaceItemIdentifier {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceItemIdentifier
    }
    fun setIdentifier(value: NSUserInterfaceItemIdentifier) {
        val sel = ObjCRuntime.sel("setIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property directoryURL
    fun directoryURL(): MemorySegment {
        val sel = ObjCRuntime.sel("directoryURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDirectoryURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDirectoryURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowedContentTypes
    /** @return NSArray<UTType *> * */
    fun allowedContentTypes(): MemorySegment {
        val sel = ObjCRuntime.sel("allowedContentTypes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAllowedContentTypes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAllowedContentTypes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsOtherFileTypes
    fun allowsOtherFileTypes(): BOOL {
        val sel = ObjCRuntime.sel("allowsOtherFileTypes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsOtherFileTypes(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsOtherFileTypes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentContentType
    fun currentContentType(): MemorySegment {
        val sel = ObjCRuntime.sel("currentContentType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCurrentContentType(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCurrentContentType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property accessoryView
    fun accessoryView(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAccessoryView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAccessoryView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSOpenSavePanelDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property expanded
    fun isExpanded(): BOOL {
        val sel = ObjCRuntime.sel("isExpanded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canCreateDirectories
    fun canCreateDirectories(): BOOL {
        val sel = ObjCRuntime.sel("canCreateDirectories")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanCreateDirectories(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanCreateDirectories:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canSelectHiddenExtension
    fun canSelectHiddenExtension(): BOOL {
        val sel = ObjCRuntime.sel("canSelectHiddenExtension")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanSelectHiddenExtension(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanSelectHiddenExtension:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property extensionHidden
    fun isExtensionHidden(): BOOL {
        val sel = ObjCRuntime.sel("isExtensionHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setExtensionHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setExtensionHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property treatsFilePackagesAsDirectories
    fun treatsFilePackagesAsDirectories(): BOOL {
        val sel = ObjCRuntime.sel("treatsFilePackagesAsDirectories")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setTreatsFilePackagesAsDirectories(value: BOOL) {
        val sel = ObjCRuntime.sel("setTreatsFilePackagesAsDirectories:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property prompt
    fun prompt(): MemorySegment {
        val sel = ObjCRuntime.sel("prompt")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPrompt(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPrompt:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun promptAsString(): String = ObjCRuntime.toJavaString(prompt())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPrompt(value: String) = setPrompt(ObjCRuntime.newNSString(Arena.global(), value))
    
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
    
    // @property nameFieldLabel
    fun nameFieldLabel(): MemorySegment {
        val sel = ObjCRuntime.sel("nameFieldLabel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNameFieldLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNameFieldLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameFieldLabelAsString(): String = ObjCRuntime.toJavaString(nameFieldLabel())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNameFieldLabel(value: String) = setNameFieldLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property nameFieldStringValue
    fun nameFieldStringValue(): MemorySegment {
        val sel = ObjCRuntime.sel("nameFieldStringValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNameFieldStringValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNameFieldStringValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nameFieldStringValueAsString(): String = ObjCRuntime.toJavaString(nameFieldStringValue())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNameFieldStringValue(value: String) = setNameFieldStringValue(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property message
    fun message(): MemorySegment {
        val sel = ObjCRuntime.sel("message")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMessage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMessage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun messageAsString(): String = ObjCRuntime.toJavaString(message())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setMessage(value: String) = setMessage(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property showsHiddenFiles
    fun showsHiddenFiles(): BOOL {
        val sel = ObjCRuntime.sel("showsHiddenFiles")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsHiddenFiles(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsHiddenFiles:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsTagField
    fun showsTagField(): BOOL {
        val sel = ObjCRuntime.sel("showsTagField")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsTagField(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsTagField:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tagNames
    /** @return NSArray<NSString *> * */
    fun tagNames(): MemorySegment {
        val sel = ObjCRuntime.sel("tagNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTagNames(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTagNames:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property showsContentTypes
    fun showsContentTypes(): BOOL {
        val sel = ObjCRuntime.sel("showsContentTypes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShowsContentTypes(value: BOOL) {
        val sel = ObjCRuntime.sel("setShowsContentTypes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSSavePanel ─────────────────────────────────────────

fun NSSavePanel.filename(): MemorySegment {
    val sel = ObjCRuntime.sel("filename")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSavePanel.directory(): MemorySegment {
    val sel = ObjCRuntime.sel("directory")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSavePanel.setDirectory(path: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setDirectory:")
    ObjCRuntime.msgSend(null, ptr, sel, path)
}

fun NSSavePanel.requiredFileType(): MemorySegment {
    val sel = ObjCRuntime.sel("requiredFileType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSavePanel.setRequiredFileType(type: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setRequiredFileType:")
    ObjCRuntime.msgSend(null, ptr, sel, type)
}

fun NSSavePanel.beginSheetForDirectory_file_modalForWindow_modalDelegate_didEndSelector_contextInfo(path: MemorySegment, name: MemorySegment, docWindow: MemorySegment, delegate: MemorySegment, didEndSelector: MemorySegment, contextInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("beginSheetForDirectory:file:modalForWindow:modalDelegate:didEndSelector:contextInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, path, name, docWindow, delegate, didEndSelector, contextInfo)
}

fun NSSavePanel.runModalForDirectory_file(path: MemorySegment, name: MemorySegment): NSInteger {
    val sel = ObjCRuntime.sel("runModalForDirectory:file:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, path, name) as NSInteger
}

fun NSSavePanel.selectText(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("selectText:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

/** @return NSArray<NSString *> * */
fun NSSavePanel.allowedFileTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("allowedFileTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSavePanel.setAllowedFileTypes(allowedFileTypes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAllowedFileTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, allowedFileTypes)
}

// @property allowedFileTypes
/** @return NSArray<NSString *> * */
fun NSSavePanel.allowedFileTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("allowedFileTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSSavePanel.setAllowedFileTypes(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setAllowedFileTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}


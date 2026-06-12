package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDocumentController
 * Superclass: NSObject
 * Protocols: NSCoding, NSMenuItemValidation, NSUserInterfaceValidations
 */
open class NSDocumentController(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDocumentController") }
        
        open fun sharedDocumentController(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedDocumentController")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun documentForURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("documentForURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    open fun documentForWindow(window: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("documentForWindow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, window) as MemorySegment
    }
    
    open fun addDocument(document: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addDocument:")
        ObjCRuntime.msgSend(null, ptr, sel, document)
    }
    
    open fun removeDocument(document: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeDocument:")
        ObjCRuntime.msgSend(null, ptr, sel, document)
    }
    
    open fun newDocument(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("newDocument:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun openUntitledDocumentAndDisplay_error(displayDocument: BOOL, outError: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("openUntitledDocumentAndDisplay:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, displayDocument, outError) as MemorySegment
    }
    
    open fun makeUntitledDocumentOfType_error(typeName: MemorySegment, outError: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("makeUntitledDocumentOfType:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeName, outError) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun makeUntitledDocumentOfType_error(typeName: String, outError: MemorySegment): MemorySegment = makeUntitledDocumentOfType_error(ObjCRuntime.newNSString(Arena.global(), typeName), outError)
    
    open fun openDocument(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("openDocument:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    /** @return NSArray<NSURL *> * */
    open fun URLsFromRunningOpenPanel(): MemorySegment {
        val sel = ObjCRuntime.sel("URLsFromRunningOpenPanel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun runModalOpenPanel_forTypes(openPanel: MemorySegment, types: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("runModalOpenPanel:forTypes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, openPanel, types) as NSInteger
    }
    
    open fun beginOpenPanelWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginOpenPanelWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    open fun beginOpenPanel_forTypes_completionHandler(openPanel: MemorySegment, inTypes: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("beginOpenPanel:forTypes:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, openPanel, inTypes, completionHandler)
    }
    
    open fun openDocumentWithContentsOfURL_display_completionHandler(url: MemorySegment, displayDocument: BOOL, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("openDocumentWithContentsOfURL:display:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, url, displayDocument, completionHandler)
    }
    
    open fun makeDocumentWithContentsOfURL_ofType_error(url: MemorySegment, typeName: MemorySegment, outError: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("makeDocumentWithContentsOfURL:ofType:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, typeName, outError) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun makeDocumentWithContentsOfURL_ofType_error(url: MemorySegment, typeName: String, outError: MemorySegment): MemorySegment = makeDocumentWithContentsOfURL_ofType_error(url, ObjCRuntime.newNSString(Arena.global(), typeName), outError)
    
    open fun reopenDocumentForURL_withContentsOfURL_display_completionHandler(urlOrNil: MemorySegment, contentsURL: MemorySegment, displayDocument: BOOL, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reopenDocumentForURL:withContentsOfURL:display:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, urlOrNil, contentsURL, displayDocument, completionHandler)
    }
    
    open fun makeDocumentForURL_withContentsOfURL_ofType_error(urlOrNil: MemorySegment, contentsURL: MemorySegment, typeName: MemorySegment, outError: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("makeDocumentForURL:withContentsOfURL:ofType:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, urlOrNil, contentsURL, typeName, outError) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun makeDocumentForURL_withContentsOfURL_ofType_error(urlOrNil: MemorySegment, contentsURL: MemorySegment, typeName: String, outError: MemorySegment): MemorySegment = makeDocumentForURL_withContentsOfURL_ofType_error(urlOrNil, contentsURL, ObjCRuntime.newNSString(Arena.global(), typeName), outError)
    
    open fun saveAllDocuments(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("saveAllDocuments:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun reviewUnsavedDocumentsWithAlertTitle_cancellable_delegate_didReviewAllSelector_contextInfo(title: MemorySegment, cancellable: BOOL, delegate: MemorySegment, didReviewAllSelector: MemorySegment, contextInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("reviewUnsavedDocumentsWithAlertTitle:cancellable:delegate:didReviewAllSelector:contextInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, title, cancellable, delegate, didReviewAllSelector, contextInfo)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun reviewUnsavedDocumentsWithAlertTitle_cancellable_delegate_didReviewAllSelector_contextInfo(title: String, cancellable: BOOL, delegate: MemorySegment, didReviewAllSelector: MemorySegment, contextInfo: MemorySegment): Unit = reviewUnsavedDocumentsWithAlertTitle_cancellable_delegate_didReviewAllSelector_contextInfo(ObjCRuntime.newNSString(Arena.global(), title), cancellable, delegate, didReviewAllSelector, contextInfo)
    
    open fun closeAllDocumentsWithDelegate_didCloseAllSelector_contextInfo(delegate: MemorySegment, didCloseAllSelector: MemorySegment, contextInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("closeAllDocumentsWithDelegate:didCloseAllSelector:contextInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, delegate, didCloseAllSelector, contextInfo)
    }
    
    open fun duplicateDocumentWithContentsOfURL_copying_displayName_error(url: MemorySegment, duplicateByCopying: BOOL, displayNameOrNil: MemorySegment, outError: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("duplicateDocumentWithContentsOfURL:copying:displayName:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, duplicateByCopying, displayNameOrNil, outError) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun duplicateDocumentWithContentsOfURL_copying_displayName_error(url: MemorySegment, duplicateByCopying: BOOL, displayNameOrNil: String, outError: MemorySegment): MemorySegment = duplicateDocumentWithContentsOfURL_copying_displayName_error(url, duplicateByCopying, ObjCRuntime.newNSString(Arena.global(), displayNameOrNil), outError)
    
    open fun standardShareMenuItem(): MemorySegment {
        val sel = ObjCRuntime.sel("standardShareMenuItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun presentError_modalForWindow_delegate_didPresentSelector_contextInfo(error: MemorySegment, window: MemorySegment, delegate: MemorySegment, didPresentSelector: MemorySegment, contextInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("presentError:modalForWindow:delegate:didPresentSelector:contextInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, error, window, delegate, didPresentSelector, contextInfo)
    }
    
    open fun presentError(error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("presentError:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
    }
    
    open fun willPresentError(error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("willPresentError:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, error) as MemorySegment
    }
    
    open fun clearRecentDocuments(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("clearRecentDocuments:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun noteNewRecentDocument(document: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("noteNewRecentDocument:")
        ObjCRuntime.msgSend(null, ptr, sel, document)
    }
    
    open fun noteNewRecentDocumentURL(url: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("noteNewRecentDocumentURL:")
        ObjCRuntime.msgSend(null, ptr, sel, url)
    }
    
    open fun typeForContentsOfURL_error(url: MemorySegment, outError: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("typeForContentsOfURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, outError) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun typeForContentsOfURL_errorAsString(url: MemorySegment, outError: MemorySegment): String = ObjCRuntime.toJavaString(typeForContentsOfURL_error(url, outError))
    
    open fun documentClassForType(typeName: MemorySegment): Class<*> {
        val sel = ObjCRuntime.sel("documentClassForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeName) as Class<*>
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun documentClassForType(typeName: String): Class<*> = documentClassForType(ObjCRuntime.newNSString(Arena.global(), typeName))
    
    open fun displayNameForType(typeName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("displayNameForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun displayNameForTypeAsString(typeName: MemorySegment): String = ObjCRuntime.toJavaString(displayNameForType(typeName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun displayNameForType(typeName: String): MemorySegment = displayNameForType(ObjCRuntime.newNSString(Arena.global(), typeName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    open fun displayNameForTypeAsString(typeName: String): String = ObjCRuntime.toJavaString(displayNameForType(ObjCRuntime.newNSString(Arena.global(), typeName)))
    
    open fun validateUserInterfaceItem(item: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("validateUserInterfaceItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, item) as BOOL
    }
    
    // @property sharedDocumentController
    /** @return NSArray<__kindof NSDocument *> * */
    open fun documents(): MemorySegment {
        val sel = ObjCRuntime.sel("documents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentDocument
    open fun currentDocument(): MemorySegment {
        val sel = ObjCRuntime.sel("currentDocument")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentDirectory
    open fun currentDirectory(): MemorySegment {
        val sel = ObjCRuntime.sel("currentDirectory")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun currentDirectoryAsString(): String = ObjCRuntime.toJavaString(currentDirectory())
    
    // @property autosavingDelay
    open fun autosavingDelay(): NSTimeInterval {
        val sel = ObjCRuntime.sel("autosavingDelay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    open fun setAutosavingDelay(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setAutosavingDelay:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasEditedDocuments
    open fun hasEditedDocuments(): BOOL {
        val sel = ObjCRuntime.sel("hasEditedDocuments")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property allowsAutomaticShareMenu
    open fun allowsAutomaticShareMenu(): BOOL {
        val sel = ObjCRuntime.sel("allowsAutomaticShareMenu")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property maximumRecentDocumentCount
    open fun maximumRecentDocumentCount(): NSUInteger {
        val sel = ObjCRuntime.sel("maximumRecentDocumentCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property recentDocumentURLs
    /** @return NSArray<NSURL *> * */
    open fun recentDocumentURLs(): MemorySegment {
        val sel = ObjCRuntime.sel("recentDocumentURLs")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property defaultType
    open fun defaultType(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun defaultTypeAsString(): String = ObjCRuntime.toJavaString(defaultType())
    
    // @property documentClassNames
    /** @return NSArray<NSString *> * */
    open fun documentClassNames(): MemorySegment {
        val sel = ObjCRuntime.sel("documentClassNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSDeprecated on NSDocumentController ─────────────────────────────────────────

fun NSDocumentController.openDocumentWithContentsOfURL_display_error(url: MemorySegment, displayDocument: BOOL, outError: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("openDocumentWithContentsOfURL:display:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, displayDocument, outError) as MemorySegment
}

fun NSDocumentController.reopenDocumentForURL_withContentsOfURL_error(url: MemorySegment, contentsURL: MemorySegment, outError: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("reopenDocumentForURL:withContentsOfURL:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, contentsURL, outError) as BOOL
}

fun NSDocumentController.fileExtensionsFromType(typeName: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("fileExtensionsFromType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeName) as MemorySegment
}

fun NSDocumentController.typeFromFileExtension(fileNameExtensionOrHFSFileType: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("typeFromFileExtension:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileNameExtensionOrHFSFileType) as MemorySegment
}

fun NSDocumentController.documentForFileName(fileName: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("documentForFileName:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileName) as MemorySegment
}

fun NSDocumentController.fileNamesFromRunningOpenPanel(): MemorySegment {
    val sel = ObjCRuntime.sel("fileNamesFromRunningOpenPanel")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDocumentController.makeDocumentWithContentsOfFile_ofType(fileName: MemorySegment, type: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("makeDocumentWithContentsOfFile:ofType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileName, type) as MemorySegment
}

fun NSDocumentController.makeDocumentWithContentsOfURL_ofType(url: MemorySegment, type: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("makeDocumentWithContentsOfURL:ofType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, type) as MemorySegment
}

fun NSDocumentController.makeUntitledDocumentOfType(type: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("makeUntitledDocumentOfType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
}

fun NSDocumentController.openDocumentWithContentsOfFile_display(fileName: MemorySegment, display: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("openDocumentWithContentsOfFile:display:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileName, display) as MemorySegment
}

fun NSDocumentController.openDocumentWithContentsOfURL_display(url: MemorySegment, display: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("openDocumentWithContentsOfURL:display:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, display) as MemorySegment
}

fun NSDocumentController.openUntitledDocumentOfType_display(type: MemorySegment, display: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("openUntitledDocumentOfType:display:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type, display) as MemorySegment
}

fun NSDocumentController.setShouldCreateUI(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setShouldCreateUI:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSDocumentController.shouldCreateUI(): BOOL {
    val sel = ObjCRuntime.sel("shouldCreateUI")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSWindowRestoration on NSDocumentController ─────────────────────────────────────────


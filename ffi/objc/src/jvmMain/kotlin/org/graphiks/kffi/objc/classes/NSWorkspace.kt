package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSWorkspace
 * Superclass: NSObject
 */
open class NSWorkspace(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWorkspace") }
        
        open fun sharedWorkspace(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedWorkspace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun openURL(url: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("openURL:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url) as BOOL
    }
    
    open fun openURL_configuration_completionHandler(url: MemorySegment, configuration: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("openURL:configuration:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, url, configuration, completionHandler)
    }
    
    open fun openURLs_withApplicationAtURL_configuration_completionHandler(urls: MemorySegment, applicationURL: MemorySegment, configuration: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("openURLs:withApplicationAtURL:configuration:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, urls, applicationURL, configuration, completionHandler)
    }
    
    open fun openApplicationAtURL_configuration_completionHandler(applicationURL: MemorySegment, configuration: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("openApplicationAtURL:configuration:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, applicationURL, configuration, completionHandler)
    }
    
    open fun selectFile_inFileViewerRootedAtPath(fullPath: MemorySegment, rootFullPath: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("selectFile:inFileViewerRootedAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath, rootFullPath) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun selectFile_inFileViewerRootedAtPath(fullPath: String, rootFullPath: String): BOOL = selectFile_inFileViewerRootedAtPath(ObjCRuntime.newNSString(Arena.global(), fullPath), ObjCRuntime.newNSString(Arena.global(), rootFullPath))
    
    open fun activateFileViewerSelectingURLs(fileURLs: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("activateFileViewerSelectingURLs:")
        ObjCRuntime.msgSend(null, ptr, sel, fileURLs)
    }
    
    open fun showSearchResultsForQueryString(queryString: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("showSearchResultsForQueryString:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, queryString) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun showSearchResultsForQueryString(queryString: String): BOOL = showSearchResultsForQueryString(ObjCRuntime.newNSString(Arena.global(), queryString))
    
    open fun noteFileSystemChanged(path: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("noteFileSystemChanged:")
        ObjCRuntime.msgSend(null, ptr, sel, path)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun noteFileSystemChanged(path: String): Unit = noteFileSystemChanged(ObjCRuntime.newNSString(Arena.global(), path))
    
    open fun isFilePackageAtPath(fullPath: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isFilePackageAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun isFilePackageAtPath(fullPath: String): BOOL = isFilePackageAtPath(ObjCRuntime.newNSString(Arena.global(), fullPath))
    
    open fun iconForFile(fullPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("iconForFile:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fullPath) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun iconForFile(fullPath: String): MemorySegment = iconForFile(ObjCRuntime.newNSString(Arena.global(), fullPath))
    
    open fun iconForFiles(fullPaths: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("iconForFiles:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fullPaths) as MemorySegment
    }
    
    open fun iconForContentType(contentType: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("iconForContentType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, contentType) as MemorySegment
    }
    
    open fun setIcon_forFile_options(image: MemorySegment, fullPath: MemorySegment, options: NSWorkspaceIconCreationOptions): BOOL {
        val sel = ObjCRuntime.sel("setIcon:forFile:options:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, image, fullPath, options) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setIcon_forFile_options(image: MemorySegment, fullPath: String, options: NSWorkspaceIconCreationOptions): BOOL = setIcon_forFile_options(image, ObjCRuntime.newNSString(Arena.global(), fullPath), options)
    
    open fun recycleURLs_completionHandler(URLs: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("recycleURLs:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, URLs, handler)
    }
    
    open fun duplicateURLs_completionHandler(URLs: MemorySegment, handler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("duplicateURLs:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, URLs, handler)
    }
    
    open fun getFileSystemInfoForPath_isRemovable_isWritable_isUnmountable_description_type(fullPath: MemorySegment, removableFlag: MemorySegment, writableFlag: MemorySegment, unmountableFlag: MemorySegment, description: MemorySegment, fileSystemType: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getFileSystemInfoForPath:isRemovable:isWritable:isUnmountable:description:type:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath, removableFlag, writableFlag, unmountableFlag, description, fileSystemType) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun getFileSystemInfoForPath_isRemovable_isWritable_isUnmountable_description_type(fullPath: String, removableFlag: MemorySegment, writableFlag: MemorySegment, unmountableFlag: MemorySegment, description: String, fileSystemType: String): BOOL = getFileSystemInfoForPath_isRemovable_isWritable_isUnmountable_description_type(ObjCRuntime.newNSString(Arena.global(), fullPath), removableFlag, writableFlag, unmountableFlag, ObjCRuntime.newNSString(Arena.global(), description), ObjCRuntime.newNSString(Arena.global(), fileSystemType))
    
    open fun unmountAndEjectDeviceAtPath(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("unmountAndEjectDeviceAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun unmountAndEjectDeviceAtPath(path: String): BOOL = unmountAndEjectDeviceAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    open fun unmountAndEjectDeviceAtURL_error(url: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("unmountAndEjectDeviceAtURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, error) as BOOL
    }
    
    open fun extendPowerOffBy(requested: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("extendPowerOffBy:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, requested) as NSInteger
    }
    
    open fun hideOtherApplications(): Unit {
        val sel = ObjCRuntime.sel("hideOtherApplications")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun URLForApplicationWithBundleIdentifier(bundleIdentifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForApplicationWithBundleIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bundleIdentifier) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun URLForApplicationWithBundleIdentifier(bundleIdentifier: String): MemorySegment = URLForApplicationWithBundleIdentifier(ObjCRuntime.newNSString(Arena.global(), bundleIdentifier))
    
    /** @return NSArray<NSURL *> * */
    open fun URLsForApplicationsWithBundleIdentifier(bundleIdentifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLsForApplicationsWithBundleIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bundleIdentifier) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun URLsForApplicationsWithBundleIdentifier(bundleIdentifier: String): MemorySegment = URLsForApplicationsWithBundleIdentifier(ObjCRuntime.newNSString(Arena.global(), bundleIdentifier))
    
    open fun URLForApplicationToOpenURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForApplicationToOpenURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    /** @return NSArray<NSURL *> * */
    open fun URLsForApplicationsToOpenURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLsForApplicationsToOpenURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    open fun setDefaultApplicationAtURL_toOpenContentTypeOfFileAtURL_completionHandler(applicationURL: MemorySegment, url: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDefaultApplicationAtURL:toOpenContentTypeOfFileAtURL:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, applicationURL, url, completionHandler)
    }
    
    open fun setDefaultApplicationAtURL_toOpenURLsWithScheme_completionHandler(applicationURL: MemorySegment, urlScheme: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDefaultApplicationAtURL:toOpenURLsWithScheme:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, applicationURL, urlScheme, completionHandler)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setDefaultApplicationAtURL_toOpenURLsWithScheme_completionHandler(applicationURL: MemorySegment, urlScheme: String, completionHandler: MemorySegment): Unit = setDefaultApplicationAtURL_toOpenURLsWithScheme_completionHandler(applicationURL, ObjCRuntime.newNSString(Arena.global(), urlScheme), completionHandler)
    
    open fun setDefaultApplicationAtURL_toOpenFileAtURL_completionHandler(applicationURL: MemorySegment, url: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDefaultApplicationAtURL:toOpenFileAtURL:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, applicationURL, url, completionHandler)
    }
    
    open fun URLForApplicationToOpenContentType(contentType: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForApplicationToOpenContentType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, contentType) as MemorySegment
    }
    
    /** @return NSArray<NSURL *> * */
    open fun URLsForApplicationsToOpenContentType(contentType: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLsForApplicationsToOpenContentType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, contentType) as MemorySegment
    }
    
    open fun setDefaultApplicationAtURL_toOpenContentType_completionHandler(applicationURL: MemorySegment, contentType: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDefaultApplicationAtURL:toOpenContentType:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, applicationURL, contentType, completionHandler)
    }
    
    // @property sharedWorkspace
    open fun notificationCenter(): MemorySegment {
        val sel = ObjCRuntime.sel("notificationCenter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fileLabels
    /** @return NSArray<NSString *> * */
    open fun fileLabels(): MemorySegment {
        val sel = ObjCRuntime.sel("fileLabels")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fileLabelColors
    /** @return NSArray<NSColor *> * */
    open fun fileLabelColors(): MemorySegment {
        val sel = ObjCRuntime.sel("fileLabelColors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property frontmostApplication
    open fun frontmostApplication(): MemorySegment {
        val sel = ObjCRuntime.sel("frontmostApplication")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property menuBarOwningApplication
    open fun menuBarOwningApplication(): MemorySegment {
        val sel = ObjCRuntime.sel("menuBarOwningApplication")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSDesktopImages on NSWorkspace ─────────────────────────────────────────

fun NSWorkspace.setDesktopImageURL_forScreen_options_error(url: MemorySegment, screen: MemorySegment, options: MemorySegment, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("setDesktopImageURL:forScreen:options:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, screen, options, error) as BOOL
}

fun NSWorkspace.desktopImageURLForScreen(screen: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("desktopImageURLForScreen:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, screen) as MemorySegment
}

/** @return NSDictionary<NSWorkspaceDesktopImageOptionKey,id> * */
fun NSWorkspace.desktopImageOptionsForScreen(screen: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("desktopImageOptionsForScreen:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, screen) as MemorySegment
}

// ── Category: NSWorkspaceAuthorization on NSWorkspace ─────────────────────────────────────────

fun NSWorkspace.requestAuthorizationOfType_completionHandler(type: NSWorkspaceAuthorizationType, completionHandler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("requestAuthorizationOfType:completionHandler:")
    ObjCRuntime.msgSend(null, ptr, sel, type, completionHandler)
}

// ── Category: NSDeprecated on NSWorkspace ─────────────────────────────────────────

fun NSWorkspace.openFile(fullPath: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("openFile:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath) as BOOL
}

fun NSWorkspace.openFile_withApplication(fullPath: MemorySegment, appName: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("openFile:withApplication:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath, appName) as BOOL
}

fun NSWorkspace.openFile_withApplication_andDeactivate(fullPath: MemorySegment, appName: MemorySegment, flag: BOOL): BOOL {
    val sel = ObjCRuntime.sel("openFile:withApplication:andDeactivate:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath, appName, flag) as BOOL
}

fun NSWorkspace.launchApplication(appName: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("launchApplication:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, appName) as BOOL
}

fun NSWorkspace.launchApplicationAtURL_options_configuration_error(url: MemorySegment, options: NSWorkspaceLaunchOptions, configuration: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("launchApplicationAtURL:options:configuration:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, options, configuration, error) as MemorySegment
}

fun NSWorkspace.openURL_options_configuration_error(url: MemorySegment, options: NSWorkspaceLaunchOptions, configuration: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("openURL:options:configuration:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, options, configuration, error) as MemorySegment
}

fun NSWorkspace.openURLs_withApplicationAtURL_options_configuration_error(urls: MemorySegment, applicationURL: MemorySegment, options: NSWorkspaceLaunchOptions, configuration: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("openURLs:withApplicationAtURL:options:configuration:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, urls, applicationURL, options, configuration, error) as MemorySegment
}

fun NSWorkspace.launchApplication_showIcon_autolaunch(appName: MemorySegment, showIcon: BOOL, autolaunch: BOOL): BOOL {
    val sel = ObjCRuntime.sel("launchApplication:showIcon:autolaunch:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, appName, showIcon, autolaunch) as BOOL
}

fun NSWorkspace.fullPathForApplication(appName: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("fullPathForApplication:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, appName) as MemorySegment
}

fun NSWorkspace.absolutePathForAppBundleWithIdentifier(bundleIdentifier: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("absolutePathForAppBundleWithIdentifier:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bundleIdentifier) as MemorySegment
}

fun NSWorkspace.launchAppWithBundleIdentifier_options_additionalEventParamDescriptor_launchIdentifier(bundleIdentifier: MemorySegment, options: NSWorkspaceLaunchOptions, descriptor: MemorySegment, identifier: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("launchAppWithBundleIdentifier:options:additionalEventParamDescriptor:launchIdentifier:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, bundleIdentifier, options, descriptor, identifier) as BOOL
}

fun NSWorkspace.openURLs_withAppBundleIdentifier_options_additionalEventParamDescriptor_launchIdentifiers(urls: MemorySegment, bundleIdentifier: MemorySegment, options: NSWorkspaceLaunchOptions, descriptor: MemorySegment, identifiers: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("openURLs:withAppBundleIdentifier:options:additionalEventParamDescriptor:launchIdentifiers:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, urls, bundleIdentifier, options, descriptor, identifiers) as BOOL
}

fun NSWorkspace.openTempFile(fullPath: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("openTempFile:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath) as BOOL
}

fun NSWorkspace.findApplications(): Unit {
    val sel = ObjCRuntime.sel("findApplications")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWorkspace.noteUserDefaultsChanged(): Unit {
    val sel = ObjCRuntime.sel("noteUserDefaultsChanged")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWorkspace.slideImage_from_to(image: MemorySegment, fromPoint: NSPoint, toPoint: NSPoint): Unit {
    val sel = ObjCRuntime.sel("slideImage:from:to:")
    ObjCRuntime.msgSend(null, ptr, sel, image, fromPoint, toPoint)
}

fun NSWorkspace.checkForRemovableMedia(): Unit {
    val sel = ObjCRuntime.sel("checkForRemovableMedia")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWorkspace.noteFileSystemChanged(): Unit {
    val sel = ObjCRuntime.sel("noteFileSystemChanged")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSWorkspace.fileSystemChanged(): BOOL {
    val sel = ObjCRuntime.sel("fileSystemChanged")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWorkspace.userDefaultsChanged(): BOOL {
    val sel = ObjCRuntime.sel("userDefaultsChanged")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWorkspace.mountNewRemovableMedia(): MemorySegment {
    val sel = ObjCRuntime.sel("mountNewRemovableMedia")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSWorkspace.activeApplication(): MemorySegment {
    val sel = ObjCRuntime.sel("activeApplication")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSWorkspace.mountedLocalVolumePaths(): MemorySegment {
    val sel = ObjCRuntime.sel("mountedLocalVolumePaths")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSWorkspace.mountedRemovableMedia(): MemorySegment {
    val sel = ObjCRuntime.sel("mountedRemovableMedia")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSWorkspace.launchedApplications(): MemorySegment {
    val sel = ObjCRuntime.sel("launchedApplications")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSWorkspace.openFile_fromImage_at_inView(fullPath: MemorySegment, image: MemorySegment, point: NSPoint, view: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("openFile:fromImage:at:inView:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath, image, point, view) as BOOL
}

fun NSWorkspace.performFileOperation_source_destination_files_tag(operation: NSWorkspaceFileOperationName, source: MemorySegment, destination: MemorySegment, files: MemorySegment, tag: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("performFileOperation:source:destination:files:tag:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, operation, source, destination, files, tag) as BOOL
}

fun NSWorkspace.getInfoForFile_application_type(fullPath: MemorySegment, appName: MemorySegment, type: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("getInfoForFile:application:type:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fullPath, appName, type) as BOOL
}

fun NSWorkspace.iconForFileType(fileType: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("iconForFileType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileType) as MemorySegment
}

fun NSWorkspace.typeOfFile_error(absoluteFilePath: MemorySegment, outError: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("typeOfFile:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, absoluteFilePath, outError) as MemorySegment
}

fun NSWorkspace.localizedDescriptionForType(typeName: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("localizedDescriptionForType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeName) as MemorySegment
}

fun NSWorkspace.preferredFilenameExtensionForType(typeName: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("preferredFilenameExtensionForType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeName) as MemorySegment
}

fun NSWorkspace.filenameExtension_isValidForType(filenameExtension: MemorySegment, typeName: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("filenameExtension:isValidForType:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, filenameExtension, typeName) as BOOL
}

fun NSWorkspace.type_conformsToType(firstTypeName: MemorySegment, secondTypeName: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("type:conformsToType:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, firstTypeName, secondTypeName) as BOOL
}

// ── Category: NSWorkspaceAccessibilityDisplay on NSWorkspace ─────────────────────────────────────────

fun NSWorkspace.accessibilityDisplayShouldIncreaseContrast(): BOOL {
    val sel = ObjCRuntime.sel("accessibilityDisplayShouldIncreaseContrast")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWorkspace.accessibilityDisplayShouldDifferentiateWithoutColor(): BOOL {
    val sel = ObjCRuntime.sel("accessibilityDisplayShouldDifferentiateWithoutColor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWorkspace.accessibilityDisplayShouldReduceTransparency(): BOOL {
    val sel = ObjCRuntime.sel("accessibilityDisplayShouldReduceTransparency")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWorkspace.accessibilityDisplayShouldReduceMotion(): BOOL {
    val sel = ObjCRuntime.sel("accessibilityDisplayShouldReduceMotion")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWorkspace.accessibilityDisplayShouldInvertColors(): BOOL {
    val sel = ObjCRuntime.sel("accessibilityDisplayShouldInvertColors")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property accessibilityDisplayShouldIncreaseContrast
fun NSWorkspace.isVoiceOverEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isVoiceOverEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSWorkspace.isSwitchControlEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isSwitchControlEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property voiceOverEnabled
/** @return NSArray<NSRunningApplication *> * */
fun NSWorkspace.runningApplications(): MemorySegment {
    val sel = ObjCRuntime.sel("runningApplications")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property runningApplications
/** @return NSArray<NSRunningApplication *> * */
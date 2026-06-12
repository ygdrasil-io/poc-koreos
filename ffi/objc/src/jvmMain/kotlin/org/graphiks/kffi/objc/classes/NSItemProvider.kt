package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSItemProvider
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSItemProvider(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSItemProvider") }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun registerDataRepresentationForTypeIdentifier_visibility_loadHandler(typeIdentifier: MemorySegment, visibility: NSItemProviderRepresentationVisibility, loadHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerDataRepresentationForTypeIdentifier:visibility:loadHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, typeIdentifier, visibility, loadHandler)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun registerDataRepresentationForTypeIdentifier_visibility_loadHandler(typeIdentifier: String, visibility: NSItemProviderRepresentationVisibility, loadHandler: MemorySegment): Unit = registerDataRepresentationForTypeIdentifier_visibility_loadHandler(ObjCRuntime.newNSString(Arena.global(), typeIdentifier), visibility, loadHandler)
    
    open fun registerFileRepresentationForTypeIdentifier_fileOptions_visibility_loadHandler(typeIdentifier: MemorySegment, fileOptions: NSItemProviderFileOptions, visibility: NSItemProviderRepresentationVisibility, loadHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerFileRepresentationForTypeIdentifier:fileOptions:visibility:loadHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, typeIdentifier, fileOptions, visibility, loadHandler)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun registerFileRepresentationForTypeIdentifier_fileOptions_visibility_loadHandler(typeIdentifier: String, fileOptions: NSItemProviderFileOptions, visibility: NSItemProviderRepresentationVisibility, loadHandler: MemorySegment): Unit = registerFileRepresentationForTypeIdentifier_fileOptions_visibility_loadHandler(ObjCRuntime.newNSString(Arena.global(), typeIdentifier), fileOptions, visibility, loadHandler)
    
    /** @return NSArray<NSString *> * */
    open fun registeredTypeIdentifiersWithFileOptions(fileOptions: NSItemProviderFileOptions): MemorySegment {
        val sel = ObjCRuntime.sel("registeredTypeIdentifiersWithFileOptions:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileOptions) as MemorySegment
    }
    
    open fun hasItemConformingToTypeIdentifier(typeIdentifier: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("hasItemConformingToTypeIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, typeIdentifier) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun hasItemConformingToTypeIdentifier(typeIdentifier: String): BOOL = hasItemConformingToTypeIdentifier(ObjCRuntime.newNSString(Arena.global(), typeIdentifier))
    
    open fun hasRepresentationConformingToTypeIdentifier_fileOptions(typeIdentifier: MemorySegment, fileOptions: NSItemProviderFileOptions): BOOL {
        val sel = ObjCRuntime.sel("hasRepresentationConformingToTypeIdentifier:fileOptions:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, typeIdentifier, fileOptions) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun hasRepresentationConformingToTypeIdentifier_fileOptions(typeIdentifier: String, fileOptions: NSItemProviderFileOptions): BOOL = hasRepresentationConformingToTypeIdentifier_fileOptions(ObjCRuntime.newNSString(Arena.global(), typeIdentifier), fileOptions)
    
    open fun loadDataRepresentationForTypeIdentifier_completionHandler(typeIdentifier: MemorySegment, completionHandler: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("loadDataRepresentationForTypeIdentifier:completionHandler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeIdentifier, completionHandler) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun loadDataRepresentationForTypeIdentifier_completionHandler(typeIdentifier: String, completionHandler: MemorySegment): MemorySegment = loadDataRepresentationForTypeIdentifier_completionHandler(ObjCRuntime.newNSString(Arena.global(), typeIdentifier), completionHandler)
    
    open fun loadFileRepresentationForTypeIdentifier_completionHandler(typeIdentifier: MemorySegment, completionHandler: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("loadFileRepresentationForTypeIdentifier:completionHandler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeIdentifier, completionHandler) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun loadFileRepresentationForTypeIdentifier_completionHandler(typeIdentifier: String, completionHandler: MemorySegment): MemorySegment = loadFileRepresentationForTypeIdentifier_completionHandler(ObjCRuntime.newNSString(Arena.global(), typeIdentifier), completionHandler)
    
    open fun loadInPlaceFileRepresentationForTypeIdentifier_completionHandler(typeIdentifier: MemorySegment, completionHandler: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("loadInPlaceFileRepresentationForTypeIdentifier:completionHandler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, typeIdentifier, completionHandler) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun loadInPlaceFileRepresentationForTypeIdentifier_completionHandler(typeIdentifier: String, completionHandler: MemorySegment): MemorySegment = loadInPlaceFileRepresentationForTypeIdentifier_completionHandler(ObjCRuntime.newNSString(Arena.global(), typeIdentifier), completionHandler)
    
    open fun initWithObject(`object`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObject:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `object`) as MemorySegment
    }
    
    open fun registerObject_visibility(`object`: MemorySegment, visibility: NSItemProviderRepresentationVisibility): Unit {
        val sel = ObjCRuntime.sel("registerObject:visibility:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`, visibility)
    }
    
    open fun registerObjectOfClass_visibility_loadHandler(aClass: MemorySegment, visibility: NSItemProviderRepresentationVisibility, loadHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerObjectOfClass:visibility:loadHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, aClass, visibility, loadHandler)
    }
    
    open fun canLoadObjectOfClass(aClass: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("canLoadObjectOfClass:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, aClass) as BOOL
    }
    
    open fun loadObjectOfClass_completionHandler(aClass: MemorySegment, completionHandler: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("loadObjectOfClass:completionHandler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aClass, completionHandler) as MemorySegment
    }
    
    open fun initWithItem_typeIdentifier(item: MemorySegment, typeIdentifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithItem:typeIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, item, typeIdentifier) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithItem_typeIdentifier(item: MemorySegment, typeIdentifier: String): MemorySegment = initWithItem_typeIdentifier(item, ObjCRuntime.newNSString(Arena.global(), typeIdentifier))
    
    open fun initWithContentsOfURL(fileURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileURL) as MemorySegment
    }
    
    open fun registerItemForTypeIdentifier_loadHandler(typeIdentifier: MemorySegment, loadHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerItemForTypeIdentifier:loadHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, typeIdentifier, loadHandler)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun registerItemForTypeIdentifier_loadHandler(typeIdentifier: String, loadHandler: MemorySegment): Unit = registerItemForTypeIdentifier_loadHandler(ObjCRuntime.newNSString(Arena.global(), typeIdentifier), loadHandler)
    
    open fun loadItemForTypeIdentifier_options_completionHandler(typeIdentifier: MemorySegment, options: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("loadItemForTypeIdentifier:options:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, typeIdentifier, options, completionHandler)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun loadItemForTypeIdentifier_options_completionHandler(typeIdentifier: String, options: MemorySegment, completionHandler: MemorySegment): Unit = loadItemForTypeIdentifier_options_completionHandler(ObjCRuntime.newNSString(Arena.global(), typeIdentifier), options, completionHandler)
    
    // @property registeredTypeIdentifiers
    /** @return NSArray<NSString *> * */
    open fun registeredTypeIdentifiers(): MemorySegment {
        val sel = ObjCRuntime.sel("registeredTypeIdentifiers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property suggestedName
    open fun suggestedName(): MemorySegment {
        val sel = ObjCRuntime.sel("suggestedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setSuggestedName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSuggestedName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun suggestedNameAsString(): String = ObjCRuntime.toJavaString(suggestedName())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setSuggestedName(value: String) = setSuggestedName(ObjCRuntime.newNSString(Arena.global(), value))
    
}

// ── Category: NSPreviewSupport on NSItemProvider ─────────────────────────────────────────

fun NSItemProvider.loadPreviewImageWithOptions_completionHandler(options: MemorySegment, completionHandler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("loadPreviewImageWithOptions:completionHandler:")
    ObjCRuntime.msgSend(null, ptr, sel, options, completionHandler)
}

fun NSItemProvider.previewImageHandler(): MemorySegment {
    val sel = ObjCRuntime.sel("previewImageHandler")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSItemProvider.setPreviewImageHandler(previewImageHandler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setPreviewImageHandler:")
    ObjCRuntime.msgSend(null, ptr, sel, previewImageHandler)
}

// @property previewImageHandler
fun NSItemProvider.previewImageHandler(): MemorySegment {
    val sel = ObjCRuntime.sel("previewImageHandler")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSItemProvider.setPreviewImageHandler(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setPreviewImageHandler:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSCloudKitSharing on NSItemProvider ─────────────────────────────────────────

fun NSItemProvider.registerCloudKitShareWithPreparationHandler(preparationHandler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("registerCloudKitShareWithPreparationHandler:")
    ObjCRuntime.msgSend(null, ptr, sel, preparationHandler)
}

fun NSItemProvider.registerCloudKitShare_container(share: MemorySegment, container: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("registerCloudKitShare:container:")
    ObjCRuntime.msgSend(null, ptr, sel, share, container)
}

// ── Category: NSItemSourceInfo on NSItemProvider ─────────────────────────────────────────

fun NSItemProvider.sourceFrame(): NSRect {
    val sel = ObjCRuntime.sel("sourceFrame")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
}

fun NSItemProvider.containerFrame(): NSRect {
    val sel = ObjCRuntime.sel("containerFrame")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
}

fun NSItemProvider.preferredPresentationSize(): NSSize {
    val sel = ObjCRuntime.sel("preferredPresentationSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}

// @property sourceFrame
fun NSItemProvider.sourceFrame(): NSRect {
    val sel = ObjCRuntime.sel("sourceFrame")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
}

// @property containerFrame
fun NSItemProvider.containerFrame(): NSRect {
    val sel = ObjCRuntime.sel("containerFrame")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
}

// @property preferredPresentationSize
fun NSItemProvider.preferredPresentationSize(): NSSize {
    val sel = ObjCRuntime.sel("preferredPresentationSize")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
}


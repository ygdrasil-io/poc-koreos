package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPersistentDocument
 * Superclass: NSDocument
 */
open class NSPersistentDocument(ptr: MemorySegment) : NSDocument(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPersistentDocument") }
        
    }
    
    fun configurePersistentStoreCoordinatorForURL_ofType_modelConfiguration_storeOptions_error(url: MemorySegment, fileType: MemorySegment, configuration: MemorySegment, storeOptions: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("configurePersistentStoreCoordinatorForURL:ofType:modelConfiguration:storeOptions:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, fileType, configuration, storeOptions, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun configurePersistentStoreCoordinatorForURL_ofType_modelConfiguration_storeOptions_error(url: MemorySegment, fileType: String, configuration: String, storeOptions: MemorySegment, error: MemorySegment): BOOL = configurePersistentStoreCoordinatorForURL_ofType_modelConfiguration_storeOptions_error(url, ObjCRuntime.newNSString(Arena.global(), fileType), ObjCRuntime.newNSString(Arena.global(), configuration), storeOptions, error)
    
    fun persistentStoreTypeForFileType(fileType: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("persistentStoreTypeForFileType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fileType) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun persistentStoreTypeForFileTypeAsString(fileType: MemorySegment): String = ObjCRuntime.toJavaString(persistentStoreTypeForFileType(fileType))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun persistentStoreTypeForFileType(fileType: String): MemorySegment = persistentStoreTypeForFileType(ObjCRuntime.newNSString(Arena.global(), fileType))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun persistentStoreTypeForFileTypeAsString(fileType: String): String = ObjCRuntime.toJavaString(persistentStoreTypeForFileType(ObjCRuntime.newNSString(Arena.global(), fileType)))
    
    override fun `writeToURL_ofType_forSaveOperation_originalContentsURL_error`(absoluteURL: MemorySegment, typeName: MemorySegment, saveOperation: NSSaveOperationType, absoluteOriginalContentsURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("writeToURL:ofType:forSaveOperation:originalContentsURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, absoluteURL, typeName, saveOperation, absoluteOriginalContentsURL, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    override fun `writeToURL_ofType_forSaveOperation_originalContentsURL_error`(absoluteURL: MemorySegment, typeName: String, saveOperation: NSSaveOperationType, absoluteOriginalContentsURL: MemorySegment, error: MemorySegment): BOOL = writeToURL_ofType_forSaveOperation_originalContentsURL_error(absoluteURL, ObjCRuntime.newNSString(Arena.global(), typeName), saveOperation, absoluteOriginalContentsURL, error)
    
    override fun `readFromURL_ofType_error`(absoluteURL: MemorySegment, typeName: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("readFromURL:ofType:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, absoluteURL, typeName, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    override fun `readFromURL_ofType_error`(absoluteURL: MemorySegment, typeName: String, error: MemorySegment): BOOL = readFromURL_ofType_error(absoluteURL, ObjCRuntime.newNSString(Arena.global(), typeName), error)
    
    override fun `revertToContentsOfURL_ofType_error`(inAbsoluteURL: MemorySegment, inTypeName: MemorySegment, outError: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("revertToContentsOfURL:ofType:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, inAbsoluteURL, inTypeName, outError) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    override fun `revertToContentsOfURL_ofType_error`(inAbsoluteURL: MemorySegment, inTypeName: String, outError: MemorySegment): BOOL = revertToContentsOfURL_ofType_error(inAbsoluteURL, ObjCRuntime.newNSString(Arena.global(), inTypeName), outError)
    
    // @property managedObjectContext
    fun managedObjectContext(): MemorySegment {
        val sel = ObjCRuntime.sel("managedObjectContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setManagedObjectContext(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setManagedObjectContext:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property managedObjectModel
    fun managedObjectModel(): MemorySegment {
        val sel = ObjCRuntime.sel("managedObjectModel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSDeprecated on NSPersistentDocument ─────────────────────────────────────────

fun NSPersistentDocument.configurePersistentStoreCoordinatorForURL_ofType_error(url: MemorySegment, fileType: MemorySegment, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("configurePersistentStoreCoordinatorForURL:ofType:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, fileType, error) as BOOL
}


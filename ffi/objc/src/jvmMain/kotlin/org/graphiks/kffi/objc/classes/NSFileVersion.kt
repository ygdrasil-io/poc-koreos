package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSFileVersion
 * Superclass: NSObject
 */
open class NSFileVersion(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFileVersion") }
        
        open fun currentVersionOfItemAtURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("currentVersionOfItemAtURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        /** @return NSArray<NSFileVersion *> * */
        open fun otherVersionsOfItemAtURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("otherVersionsOfItemAtURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        /** @return NSArray<NSFileVersion *> * */
        open fun unresolvedConflictVersionsOfItemAtURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unresolvedConflictVersionsOfItemAtURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        open fun getNonlocalVersionsOfItemAtURL_completionHandler(url: MemorySegment, completionHandler: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("getNonlocalVersionsOfItemAtURL:completionHandler:")
            ObjCRuntime.msgSend(null, _class, sel, url, completionHandler)
        }
        
        open fun versionOfItemAtURL_forPersistentIdentifier(url: MemorySegment, persistentIdentifier: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("versionOfItemAtURL:forPersistentIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url, persistentIdentifier) as MemorySegment
        }
        
        open fun addVersionOfItemAtURL_withContentsOfURL_options_error(url: MemorySegment, contentsURL: MemorySegment, options: NSFileVersionAddingOptions, outError: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("addVersionOfItemAtURL:withContentsOfURL:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url, contentsURL, options, outError) as MemorySegment
        }
        
        open fun temporaryDirectoryURLForNewVersionOfItemAtURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("temporaryDirectoryURLForNewVersionOfItemAtURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        open fun removeOtherVersionsOfItemAtURL_error(url: MemorySegment, outError: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("removeOtherVersionsOfItemAtURL:error:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, url, outError) as BOOL
        }
        
    }
    
    open fun replaceItemAtURL_options_error(url: MemorySegment, options: NSFileVersionReplacingOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("replaceItemAtURL:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, options, error) as MemorySegment
    }
    
    open fun removeAndReturnError(outError: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeAndReturnError:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, outError) as BOOL
    }
    
    // @property URL
    open fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localizedName
    open fun localizedName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedNameAsString(): String = ObjCRuntime.toJavaString(localizedName())
    
    // @property localizedNameOfSavingComputer
    open fun localizedNameOfSavingComputer(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedNameOfSavingComputer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedNameOfSavingComputerAsString(): String = ObjCRuntime.toJavaString(localizedNameOfSavingComputer())
    
    // @property originatorNameComponents
    open fun originatorNameComponents(): MemorySegment {
        val sel = ObjCRuntime.sel("originatorNameComponents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property modificationDate
    open fun modificationDate(): MemorySegment {
        val sel = ObjCRuntime.sel("modificationDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property persistentIdentifier
    /** @return id<NSCoding> */
    open fun persistentIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("persistentIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property conflict
    open fun isConflict(): BOOL {
        val sel = ObjCRuntime.sel("isConflict")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property resolved
    open fun isResolved(): BOOL {
        val sel = ObjCRuntime.sel("isResolved")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setResolved(value: BOOL) {
        val sel = ObjCRuntime.sel("setResolved:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property discardable
    open fun isDiscardable(): BOOL {
        val sel = ObjCRuntime.sel("isDiscardable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setDiscardable(value: BOOL) {
        val sel = ObjCRuntime.sel("setDiscardable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasLocalContents
    open fun hasLocalContents(): BOOL {
        val sel = ObjCRuntime.sel("hasLocalContents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property hasThumbnail
    open fun hasThumbnail(): BOOL {
        val sel = ObjCRuntime.sel("hasThumbnail")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}


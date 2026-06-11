/**
 * Kotlin/JVM wrapper for Objective-C class: NSFileVersion
 * Superclass: NSObject
 */
open class NSFileVersion(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFileVersion") }
        
        fun currentVersionOfItemAtURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("currentVersionOfItemAtURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        /** @return NSArray<NSFileVersion *> * */
        fun otherVersionsOfItemAtURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("otherVersionsOfItemAtURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        /** @return NSArray<NSFileVersion *> * */
        fun unresolvedConflictVersionsOfItemAtURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unresolvedConflictVersionsOfItemAtURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        fun getNonlocalVersionsOfItemAtURL_completionHandler(url: MemorySegment, completionHandler: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("getNonlocalVersionsOfItemAtURL:completionHandler:")
            ObjCRuntime.msgSend(null, _class, sel, url, completionHandler)
        }
        
        fun versionOfItemAtURL_forPersistentIdentifier(url: MemorySegment, persistentIdentifier: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("versionOfItemAtURL:forPersistentIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url, persistentIdentifier) as MemorySegment
        }
        
        fun addVersionOfItemAtURL_withContentsOfURL_options_error(url: MemorySegment, contentsURL: MemorySegment, options: NSFileVersionAddingOptions, outError: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("addVersionOfItemAtURL:withContentsOfURL:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url, contentsURL, options, outError) as MemorySegment
        }
        
        fun temporaryDirectoryURLForNewVersionOfItemAtURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("temporaryDirectoryURLForNewVersionOfItemAtURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        fun removeOtherVersionsOfItemAtURL_error(url: MemorySegment, outError: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("removeOtherVersionsOfItemAtURL:error:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, url, outError) as BOOL
        }
        
    }
    
    fun replaceItemAtURL_options_error(url: MemorySegment, options: NSFileVersionReplacingOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("replaceItemAtURL:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, options, error) as MemorySegment
    }
    
    fun removeAndReturnError(outError: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeAndReturnError:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, outError) as BOOL
    }
    
    // @property URL
    fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localizedName
    fun localizedName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedNameAsString(): String = ObjCRuntime.toJavaString(localizedName())
    
    // @property localizedNameOfSavingComputer
    fun localizedNameOfSavingComputer(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedNameOfSavingComputer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedNameOfSavingComputerAsString(): String = ObjCRuntime.toJavaString(localizedNameOfSavingComputer())
    
    // @property originatorNameComponents
    fun originatorNameComponents(): MemorySegment {
        val sel = ObjCRuntime.sel("originatorNameComponents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property modificationDate
    fun modificationDate(): MemorySegment {
        val sel = ObjCRuntime.sel("modificationDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property persistentIdentifier
    /** @return id<NSCoding> */
    fun persistentIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("persistentIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property conflict
    fun isConflict(): BOOL {
        val sel = ObjCRuntime.sel("isConflict")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property resolved
    fun isResolved(): BOOL {
        val sel = ObjCRuntime.sel("isResolved")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setResolved(value: BOOL) {
        val sel = ObjCRuntime.sel("setResolved:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property discardable
    fun isDiscardable(): BOOL {
        val sel = ObjCRuntime.sel("isDiscardable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDiscardable(value: BOOL) {
        val sel = ObjCRuntime.sel("setDiscardable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasLocalContents
    fun hasLocalContents(): BOOL {
        val sel = ObjCRuntime.sel("hasLocalContents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property hasThumbnail
    fun hasThumbnail(): BOOL {
        val sel = ObjCRuntime.sel("hasThumbnail")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}


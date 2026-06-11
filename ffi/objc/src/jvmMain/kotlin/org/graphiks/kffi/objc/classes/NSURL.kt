/**
 * Kotlin/JVM wrapper for Objective-C class: NSURL
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSURL(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURL") }
        
        fun fileURLWithPath_isDirectory_relativeToURL(path: MemorySegment, isDir: BOOL, baseURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fileURLWithPath:isDirectory:relativeToURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path, isDir, baseURL) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun fileURLWithPath_isDirectory_relativeToURL(path: String, isDir: BOOL, baseURL: MemorySegment): MemorySegment = fileURLWithPath_isDirectory_relativeToURL(ObjCRuntime.newNSString(Arena.global(), path), isDir, baseURL)
        
        fun fileURLWithPath_relativeToURL(path: MemorySegment, baseURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fileURLWithPath:relativeToURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path, baseURL) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun fileURLWithPath_relativeToURL(path: String, baseURL: MemorySegment): MemorySegment = fileURLWithPath_relativeToURL(ObjCRuntime.newNSString(Arena.global(), path), baseURL)
        
        fun fileURLWithPath_isDirectory(path: MemorySegment, isDir: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("fileURLWithPath:isDirectory:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path, isDir) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun fileURLWithPath_isDirectory(path: String, isDir: BOOL): MemorySegment = fileURLWithPath_isDirectory(ObjCRuntime.newNSString(Arena.global(), path), isDir)
        
        fun fileURLWithPath(path: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fileURLWithPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun fileURLWithPath(path: String): MemorySegment = fileURLWithPath(ObjCRuntime.newNSString(Arena.global(), path))
        
        fun fileURLWithFileSystemRepresentation_isDirectory_relativeToURL(path: MemorySegment, isDir: BOOL, baseURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("fileURLWithFileSystemRepresentation:isDirectory:relativeToURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path, isDir, baseURL) as MemorySegment
        }
        
        fun URLWithString(URLString: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("URLWithString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, URLString) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun URLWithString(URLString: String): MemorySegment = URLWithString(ObjCRuntime.newNSString(Arena.global(), URLString))
        
        fun URLWithString_relativeToURL(URLString: MemorySegment, baseURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("URLWithString:relativeToURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, URLString, baseURL) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun URLWithString_relativeToURL(URLString: String, baseURL: MemorySegment): MemorySegment = URLWithString_relativeToURL(ObjCRuntime.newNSString(Arena.global(), URLString), baseURL)
        
        fun URLWithString_encodingInvalidCharacters(URLString: MemorySegment, encodingInvalidCharacters: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("URLWithString:encodingInvalidCharacters:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, URLString, encodingInvalidCharacters) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun URLWithString_encodingInvalidCharacters(URLString: String, encodingInvalidCharacters: BOOL): MemorySegment = URLWithString_encodingInvalidCharacters(ObjCRuntime.newNSString(Arena.global(), URLString), encodingInvalidCharacters)
        
        fun URLWithDataRepresentation_relativeToURL(`data`: MemorySegment, baseURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("URLWithDataRepresentation:relativeToURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`, baseURL) as MemorySegment
        }
        
        fun absoluteURLWithDataRepresentation_relativeToURL(`data`: MemorySegment, baseURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("absoluteURLWithDataRepresentation:relativeToURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`, baseURL) as MemorySegment
        }
        
        fun URLByResolvingBookmarkData_options_relativeToURL_bookmarkDataIsStale_error(bookmarkData: MemorySegment, options: NSURLBookmarkResolutionOptions, relativeURL: MemorySegment, isStale: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("URLByResolvingBookmarkData:options:relativeToURL:bookmarkDataIsStale:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, bookmarkData, options, relativeURL, isStale, error) as MemorySegment
        }
        
        /** @return NSDictionary<NSURLResourceKey,id> * */
        fun resourceValuesForKeys_fromBookmarkData(keys: MemorySegment, bookmarkData: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("resourceValuesForKeys:fromBookmarkData:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, keys, bookmarkData) as MemorySegment
        }
        
        fun writeBookmarkData_toURL_options_error(bookmarkData: MemorySegment, bookmarkFileURL: MemorySegment, options: NSURLBookmarkFileCreationOptions, error: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("writeBookmarkData:toURL:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, bookmarkData, bookmarkFileURL, options, error) as BOOL
        }
        
        fun bookmarkDataWithContentsOfURL_error(bookmarkFileURL: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("bookmarkDataWithContentsOfURL:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, bookmarkFileURL, error) as MemorySegment
        }
        
        fun URLByResolvingAliasFileAtURL_options_error(url: MemorySegment, options: NSURLBookmarkResolutionOptions, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("URLByResolvingAliasFileAtURL:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url, options, error) as MemorySegment
        }
        
    }
    
    fun initWithScheme_host_path(scheme: MemorySegment, host: MemorySegment, path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithScheme:host:path:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, scheme, host, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithScheme_host_path(scheme: String, host: String, path: String): MemorySegment = initWithScheme_host_path(ObjCRuntime.newNSString(Arena.global(), scheme), ObjCRuntime.newNSString(Arena.global(), host), ObjCRuntime.newNSString(Arena.global(), path))
    
    fun initFileURLWithPath_isDirectory_relativeToURL(path: MemorySegment, isDir: BOOL, baseURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initFileURLWithPath:isDirectory:relativeToURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, isDir, baseURL) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initFileURLWithPath_isDirectory_relativeToURL(path: String, isDir: BOOL, baseURL: MemorySegment): MemorySegment = initFileURLWithPath_isDirectory_relativeToURL(ObjCRuntime.newNSString(Arena.global(), path), isDir, baseURL)
    
    fun initFileURLWithPath_relativeToURL(path: MemorySegment, baseURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initFileURLWithPath:relativeToURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, baseURL) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initFileURLWithPath_relativeToURL(path: String, baseURL: MemorySegment): MemorySegment = initFileURLWithPath_relativeToURL(ObjCRuntime.newNSString(Arena.global(), path), baseURL)
    
    fun initFileURLWithPath_isDirectory(path: MemorySegment, isDir: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initFileURLWithPath:isDirectory:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, isDir) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initFileURLWithPath_isDirectory(path: String, isDir: BOOL): MemorySegment = initFileURLWithPath_isDirectory(ObjCRuntime.newNSString(Arena.global(), path), isDir)
    
    fun initFileURLWithPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initFileURLWithPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initFileURLWithPath(path: String): MemorySegment = initFileURLWithPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun initFileURLWithFileSystemRepresentation_isDirectory_relativeToURL(path: MemorySegment, isDir: BOOL, baseURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initFileURLWithFileSystemRepresentation:isDirectory:relativeToURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, isDir, baseURL) as MemorySegment
    }
    
    fun initWithString(URLString: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URLString) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString(URLString: String): MemorySegment = initWithString(ObjCRuntime.newNSString(Arena.global(), URLString))
    
    fun initWithString_relativeToURL(URLString: MemorySegment, baseURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:relativeToURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URLString, baseURL) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString_relativeToURL(URLString: String, baseURL: MemorySegment): MemorySegment = initWithString_relativeToURL(ObjCRuntime.newNSString(Arena.global(), URLString), baseURL)
    
    fun initWithString_encodingInvalidCharacters(URLString: MemorySegment, encodingInvalidCharacters: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:encodingInvalidCharacters:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URLString, encodingInvalidCharacters) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString_encodingInvalidCharacters(URLString: String, encodingInvalidCharacters: BOOL): MemorySegment = initWithString_encodingInvalidCharacters(ObjCRuntime.newNSString(Arena.global(), URLString), encodingInvalidCharacters)
    
    fun initWithDataRepresentation_relativeToURL(`data`: MemorySegment, baseURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDataRepresentation:relativeToURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, baseURL) as MemorySegment
    }
    
    fun initAbsoluteURLWithDataRepresentation_relativeToURL(`data`: MemorySegment, baseURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initAbsoluteURLWithDataRepresentation:relativeToURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, baseURL) as MemorySegment
    }
    
    fun getFileSystemRepresentation_maxLength(buffer: MemorySegment, maxBufferLength: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("getFileSystemRepresentation:maxLength:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, buffer, maxBufferLength) as BOOL
    }
    
    fun isFileReferenceURL(): BOOL {
        val sel = ObjCRuntime.sel("isFileReferenceURL")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun fileReferenceURL(): MemorySegment {
        val sel = ObjCRuntime.sel("fileReferenceURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun getResourceValue_forKey_error(value: MemorySegment, key: NSURLResourceKey, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getResourceValue:forKey:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, value, key, error) as BOOL
    }
    
    /** @return NSDictionary<NSURLResourceKey,id> * */
    fun resourceValuesForKeys_error(keys: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("resourceValuesForKeys:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keys, error) as MemorySegment
    }
    
    fun setResourceValue_forKey_error(value: MemorySegment, key: NSURLResourceKey, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setResourceValue:forKey:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, value, key, error) as BOOL
    }
    
    fun setResourceValues_error(keyedValues: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setResourceValues:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, keyedValues, error) as BOOL
    }
    
    fun removeCachedResourceValueForKey(key: NSURLResourceKey): Unit {
        val sel = ObjCRuntime.sel("removeCachedResourceValueForKey:")
        ObjCRuntime.msgSend(null, ptr, sel, key)
    }
    
    fun removeAllCachedResourceValues(): Unit {
        val sel = ObjCRuntime.sel("removeAllCachedResourceValues")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setTemporaryResourceValue_forKey(value: MemorySegment, key: NSURLResourceKey): Unit {
        val sel = ObjCRuntime.sel("setTemporaryResourceValue:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, key)
    }
    
    fun bookmarkDataWithOptions_includingResourceValuesForKeys_relativeToURL_error(options: NSURLBookmarkCreationOptions, keys: MemorySegment, relativeURL: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("bookmarkDataWithOptions:includingResourceValuesForKeys:relativeToURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options, keys, relativeURL, error) as MemorySegment
    }
    
    fun initByResolvingBookmarkData_options_relativeToURL_bookmarkDataIsStale_error(bookmarkData: MemorySegment, options: NSURLBookmarkResolutionOptions, relativeURL: MemorySegment, isStale: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initByResolvingBookmarkData:options:relativeToURL:bookmarkDataIsStale:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, bookmarkData, options, relativeURL, isStale, error) as MemorySegment
    }
    
    fun startAccessingSecurityScopedResource(): BOOL {
        val sel = ObjCRuntime.sel("startAccessingSecurityScopedResource")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun stopAccessingSecurityScopedResource(): Unit {
        val sel = ObjCRuntime.sel("stopAccessingSecurityScopedResource")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property dataRepresentation
    fun dataRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("dataRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property absoluteString
    fun absoluteString(): MemorySegment {
        val sel = ObjCRuntime.sel("absoluteString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun absoluteStringAsString(): String = ObjCRuntime.toJavaString(absoluteString())
    
    // @property relativeString
    fun relativeString(): MemorySegment {
        val sel = ObjCRuntime.sel("relativeString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun relativeStringAsString(): String = ObjCRuntime.toJavaString(relativeString())
    
    // @property baseURL
    fun baseURL(): MemorySegment {
        val sel = ObjCRuntime.sel("baseURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property absoluteURL
    fun absoluteURL(): MemorySegment {
        val sel = ObjCRuntime.sel("absoluteURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property scheme
    fun scheme(): MemorySegment {
        val sel = ObjCRuntime.sel("scheme")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun schemeAsString(): String = ObjCRuntime.toJavaString(scheme())
    
    // @property resourceSpecifier
    fun resourceSpecifier(): MemorySegment {
        val sel = ObjCRuntime.sel("resourceSpecifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun resourceSpecifierAsString(): String = ObjCRuntime.toJavaString(resourceSpecifier())
    
    // @property host
    fun host(): MemorySegment {
        val sel = ObjCRuntime.sel("host")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun hostAsString(): String = ObjCRuntime.toJavaString(host())
    
    // @property port
    fun port(): MemorySegment {
        val sel = ObjCRuntime.sel("port")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property user
    fun user(): MemorySegment {
        val sel = ObjCRuntime.sel("user")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun userAsString(): String = ObjCRuntime.toJavaString(user())
    
    // @property password
    fun password(): MemorySegment {
        val sel = ObjCRuntime.sel("password")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun passwordAsString(): String = ObjCRuntime.toJavaString(password())
    
    // @property path
    fun path(): MemorySegment {
        val sel = ObjCRuntime.sel("path")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathAsString(): String = ObjCRuntime.toJavaString(path())
    
    // @property fragment
    fun fragment(): MemorySegment {
        val sel = ObjCRuntime.sel("fragment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun fragmentAsString(): String = ObjCRuntime.toJavaString(fragment())
    
    // @property parameterString
    fun parameterString(): MemorySegment {
        val sel = ObjCRuntime.sel("parameterString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun parameterStringAsString(): String = ObjCRuntime.toJavaString(parameterString())
    
    // @property query
    fun query(): MemorySegment {
        val sel = ObjCRuntime.sel("query")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun queryAsString(): String = ObjCRuntime.toJavaString(query())
    
    // @property relativePath
    fun relativePath(): MemorySegment {
        val sel = ObjCRuntime.sel("relativePath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun relativePathAsString(): String = ObjCRuntime.toJavaString(relativePath())
    
    // @property hasDirectoryPath
    fun hasDirectoryPath(): BOOL {
        val sel = ObjCRuntime.sel("hasDirectoryPath")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property fileSystemRepresentation
    fun fileSystemRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("fileSystemRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fileURL
    fun isFileURL(): BOOL {
        val sel = ObjCRuntime.sel("isFileURL")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property standardizedURL
    fun standardizedURL(): MemorySegment {
        val sel = ObjCRuntime.sel("standardizedURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property filePathURL
    fun filePathURL(): MemorySegment {
        val sel = ObjCRuntime.sel("filePathURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _urlString: MemorySegment
    // ivar: _baseURL: MemorySegment
    // ivar: _clients: MemorySegment
    // ivar: _reserved: MemorySegment
}

// ── Category: NSPromisedItems on NSURL ─────────────────────────────────────────

fun NSURL.getPromisedItemResourceValue_forKey_error(value: MemorySegment, key: NSURLResourceKey, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("getPromisedItemResourceValue:forKey:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, value, key, error) as BOOL
}

/** @return NSDictionary<NSURLResourceKey,id> * */
fun NSURL.promisedItemResourceValuesForKeys_error(keys: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("promisedItemResourceValuesForKeys:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keys, error) as MemorySegment
}

fun NSURL.checkPromisedItemIsReachableAndReturnError(error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("checkPromisedItemIsReachableAndReturnError:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
}

// ── Category: NSItemProvider on NSURL ─────────────────────────────────────────

// ── Category: NSURLPathUtilities on NSURL ─────────────────────────────────────────

fun NSURL.URLByAppendingPathComponent(pathComponent: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("URLByAppendingPathComponent:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pathComponent) as MemorySegment
}

fun NSURL.URLByAppendingPathComponent_isDirectory(pathComponent: MemorySegment, isDirectory: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("URLByAppendingPathComponent:isDirectory:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pathComponent, isDirectory) as MemorySegment
}

fun NSURL.URLByAppendingPathExtension(pathExtension: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("URLByAppendingPathExtension:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pathExtension) as MemorySegment
}

fun NSURL.checkResourceIsReachableAndReturnError(error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("checkResourceIsReachableAndReturnError:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
}

/** @return NSArray<NSString *> * */
fun NSURL.pathComponents(): MemorySegment {
    val sel = ObjCRuntime.sel("pathComponents")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURL.lastPathComponent(): MemorySegment {
    val sel = ObjCRuntime.sel("lastPathComponent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURL.pathExtension(): MemorySegment {
    val sel = ObjCRuntime.sel("pathExtension")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURL.URLByDeletingLastPathComponent(): MemorySegment {
    val sel = ObjCRuntime.sel("URLByDeletingLastPathComponent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURL.URLByDeletingPathExtension(): MemorySegment {
    val sel = ObjCRuntime.sel("URLByDeletingPathExtension")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURL.URLByStandardizingPath(): MemorySegment {
    val sel = ObjCRuntime.sel("URLByStandardizingPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURL.URLByResolvingSymlinksInPath(): MemorySegment {
    val sel = ObjCRuntime.sel("URLByResolvingSymlinksInPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class method: +[NSURL fileURLWithPathComponents:]
fun NSURL_fileURLWithPathComponents(components: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("fileURLWithPathComponents:")
    val cls = ObjCRuntime.getClass("NSURL")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, components) as MemorySegment
}

// @property pathComponents
/** @return NSArray<NSString *> * */
fun NSURL.pathComponents(): MemorySegment {
    val sel = ObjCRuntime.sel("pathComponents")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property lastPathComponent
fun NSURL.lastPathComponent(): MemorySegment {
    val sel = ObjCRuntime.sel("lastPathComponent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property pathExtension
fun NSURL.pathExtension(): MemorySegment {
    val sel = ObjCRuntime.sel("pathExtension")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property URLByDeletingLastPathComponent
fun NSURL.URLByDeletingLastPathComponent(): MemorySegment {
    val sel = ObjCRuntime.sel("URLByDeletingLastPathComponent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property URLByDeletingPathExtension
fun NSURL.URLByDeletingPathExtension(): MemorySegment {
    val sel = ObjCRuntime.sel("URLByDeletingPathExtension")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property URLByStandardizingPath
fun NSURL.URLByStandardizingPath(): MemorySegment {
    val sel = ObjCRuntime.sel("URLByStandardizingPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property URLByResolvingSymlinksInPath
fun NSURL.URLByResolvingSymlinksInPath(): MemorySegment {
    val sel = ObjCRuntime.sel("URLByResolvingSymlinksInPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSURLLoading on NSURL ─────────────────────────────────────────

fun NSURL.resourceDataUsingCache(shouldUseCache: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("resourceDataUsingCache:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, shouldUseCache) as MemorySegment
}

fun NSURL.loadResourceDataNotifyingClient_usingCache(client: MemorySegment, shouldUseCache: BOOL): Unit {
    val sel = ObjCRuntime.sel("loadResourceDataNotifyingClient:usingCache:")
    ObjCRuntime.msgSend(null, ptr, sel, client, shouldUseCache)
}

fun NSURL.propertyForKey(propertyKey: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("propertyForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, propertyKey) as MemorySegment
}

fun NSURL.setResourceData(`data`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("setResourceData:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `data`) as BOOL
}

fun NSURL.setProperty_forKey(property: MemorySegment, propertyKey: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("setProperty:forKey:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, property, propertyKey) as BOOL
}

fun NSURL.URLHandleUsingCache(shouldUseCache: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("URLHandleUsingCache:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, shouldUseCache) as MemorySegment
}

// ── Category: NSPasteboardSupport on NSURL ─────────────────────────────────────────

fun NSURL.writeToPasteboard(pasteBoard: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("writeToPasteboard:")
    ObjCRuntime.msgSend(null, ptr, sel, pasteBoard)
}

// Class method: +[NSURL URLFromPasteboard:]
fun NSURL_URLFromPasteboard(pasteBoard: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("URLFromPasteboard:")
    val cls = ObjCRuntime.getClass("NSURL")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, pasteBoard) as MemorySegment
}


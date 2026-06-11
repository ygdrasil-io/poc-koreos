/**
 * Kotlin/JVM wrapper for Objective-C class: NSFileManager
 * Superclass: NSObject
 */
open class NSFileManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFileManager") }
        
        fun defaultManager(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultManager")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    /** @return NSArray<NSURL *> * */
    fun mountedVolumeURLsIncludingResourceValuesForKeys_options(propertyKeys: MemorySegment, options: NSVolumeEnumerationOptions): MemorySegment {
        val sel = ObjCRuntime.sel("mountedVolumeURLsIncludingResourceValuesForKeys:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, propertyKeys, options) as MemorySegment
    }
    
    fun unmountVolumeAtURL_options_completionHandler(url: MemorySegment, mask: NSFileManagerUnmountOptions, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("unmountVolumeAtURL:options:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, url, mask, completionHandler)
    }
    
    /** @return NSArray<NSURL *> * */
    fun contentsOfDirectoryAtURL_includingPropertiesForKeys_options_error(url: MemorySegment, keys: MemorySegment, mask: NSDirectoryEnumerationOptions, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("contentsOfDirectoryAtURL:includingPropertiesForKeys:options:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, keys, mask, error) as MemorySegment
    }
    
    /** @return NSArray<NSURL *> * */
    fun URLsForDirectory_inDomains(directory: NSSearchPathDirectory, domainMask: NSSearchPathDomainMask): MemorySegment {
        val sel = ObjCRuntime.sel("URLsForDirectory:inDomains:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, directory, domainMask) as MemorySegment
    }
    
    fun URLForDirectory_inDomain_appropriateForURL_create_error(directory: NSSearchPathDirectory, domain: NSSearchPathDomainMask, url: MemorySegment, shouldCreate: BOOL, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForDirectory:inDomain:appropriateForURL:create:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, directory, domain, url, shouldCreate, error) as MemorySegment
    }
    
    fun getRelationship_ofDirectoryAtURL_toItemAtURL_error(outRelationship: MemorySegment, directoryURL: MemorySegment, otherURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getRelationship:ofDirectoryAtURL:toItemAtURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, outRelationship, directoryURL, otherURL, error) as BOOL
    }
    
    fun getRelationship_ofDirectory_inDomain_toItemAtURL_error(outRelationship: MemorySegment, directory: NSSearchPathDirectory, domainMask: NSSearchPathDomainMask, url: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getRelationship:ofDirectory:inDomain:toItemAtURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, outRelationship, directory, domainMask, url, error) as BOOL
    }
    
    fun createDirectoryAtURL_withIntermediateDirectories_attributes_error(url: MemorySegment, createIntermediates: BOOL, attributes: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("createDirectoryAtURL:withIntermediateDirectories:attributes:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, createIntermediates, attributes, error) as BOOL
    }
    
    fun createSymbolicLinkAtURL_withDestinationURL_error(url: MemorySegment, destURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("createSymbolicLinkAtURL:withDestinationURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, destURL, error) as BOOL
    }
    
    fun setAttributes_ofItemAtPath_error(attributes: MemorySegment, path: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setAttributes:ofItemAtPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, attributes, path, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setAttributes_ofItemAtPath_error(attributes: MemorySegment, path: String, error: MemorySegment): BOOL = setAttributes_ofItemAtPath_error(attributes, ObjCRuntime.newNSString(Arena.global(), path), error)
    
    fun createDirectoryAtPath_withIntermediateDirectories_attributes_error(path: MemorySegment, createIntermediates: BOOL, attributes: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("createDirectoryAtPath:withIntermediateDirectories:attributes:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, createIntermediates, attributes, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun createDirectoryAtPath_withIntermediateDirectories_attributes_error(path: String, createIntermediates: BOOL, attributes: MemorySegment, error: MemorySegment): BOOL = createDirectoryAtPath_withIntermediateDirectories_attributes_error(ObjCRuntime.newNSString(Arena.global(), path), createIntermediates, attributes, error)
    
    /** @return NSArray<NSString *> * */
    fun contentsOfDirectoryAtPath_error(path: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("contentsOfDirectoryAtPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun contentsOfDirectoryAtPath_error(path: String, error: MemorySegment): MemorySegment = contentsOfDirectoryAtPath_error(ObjCRuntime.newNSString(Arena.global(), path), error)
    
    /** @return NSArray<NSString *> * */
    fun subpathsOfDirectoryAtPath_error(path: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("subpathsOfDirectoryAtPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun subpathsOfDirectoryAtPath_error(path: String, error: MemorySegment): MemorySegment = subpathsOfDirectoryAtPath_error(ObjCRuntime.newNSString(Arena.global(), path), error)
    
    /** @return NSDictionary<NSFileAttributeKey,id> * */
    fun attributesOfItemAtPath_error(path: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("attributesOfItemAtPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun attributesOfItemAtPath_error(path: String, error: MemorySegment): MemorySegment = attributesOfItemAtPath_error(ObjCRuntime.newNSString(Arena.global(), path), error)
    
    /** @return NSDictionary<NSFileAttributeKey,id> * */
    fun attributesOfFileSystemForPath_error(path: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("attributesOfFileSystemForPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun attributesOfFileSystemForPath_error(path: String, error: MemorySegment): MemorySegment = attributesOfFileSystemForPath_error(ObjCRuntime.newNSString(Arena.global(), path), error)
    
    fun createSymbolicLinkAtPath_withDestinationPath_error(path: MemorySegment, destPath: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("createSymbolicLinkAtPath:withDestinationPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, destPath, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun createSymbolicLinkAtPath_withDestinationPath_error(path: String, destPath: String, error: MemorySegment): BOOL = createSymbolicLinkAtPath_withDestinationPath_error(ObjCRuntime.newNSString(Arena.global(), path), ObjCRuntime.newNSString(Arena.global(), destPath), error)
    
    fun destinationOfSymbolicLinkAtPath_error(path: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("destinationOfSymbolicLinkAtPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, error) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun destinationOfSymbolicLinkAtPath_errorAsString(path: MemorySegment, error: MemorySegment): String = ObjCRuntime.toJavaString(destinationOfSymbolicLinkAtPath_error(path, error))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun destinationOfSymbolicLinkAtPath_error(path: String, error: MemorySegment): MemorySegment = destinationOfSymbolicLinkAtPath_error(ObjCRuntime.newNSString(Arena.global(), path), error)
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun destinationOfSymbolicLinkAtPath_errorAsString(path: String, error: MemorySegment): String = ObjCRuntime.toJavaString(destinationOfSymbolicLinkAtPath_error(ObjCRuntime.newNSString(Arena.global(), path), error))
    
    fun copyItemAtPath_toPath_error(srcPath: MemorySegment, dstPath: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("copyItemAtPath:toPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, srcPath, dstPath, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun copyItemAtPath_toPath_error(srcPath: String, dstPath: String, error: MemorySegment): BOOL = copyItemAtPath_toPath_error(ObjCRuntime.newNSString(Arena.global(), srcPath), ObjCRuntime.newNSString(Arena.global(), dstPath), error)
    
    fun moveItemAtPath_toPath_error(srcPath: MemorySegment, dstPath: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("moveItemAtPath:toPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, srcPath, dstPath, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun moveItemAtPath_toPath_error(srcPath: String, dstPath: String, error: MemorySegment): BOOL = moveItemAtPath_toPath_error(ObjCRuntime.newNSString(Arena.global(), srcPath), ObjCRuntime.newNSString(Arena.global(), dstPath), error)
    
    fun linkItemAtPath_toPath_error(srcPath: MemorySegment, dstPath: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("linkItemAtPath:toPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, srcPath, dstPath, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun linkItemAtPath_toPath_error(srcPath: String, dstPath: String, error: MemorySegment): BOOL = linkItemAtPath_toPath_error(ObjCRuntime.newNSString(Arena.global(), srcPath), ObjCRuntime.newNSString(Arena.global(), dstPath), error)
    
    fun removeItemAtPath_error(path: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeItemAtPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun removeItemAtPath_error(path: String, error: MemorySegment): BOOL = removeItemAtPath_error(ObjCRuntime.newNSString(Arena.global(), path), error)
    
    fun copyItemAtURL_toURL_error(srcURL: MemorySegment, dstURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("copyItemAtURL:toURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, srcURL, dstURL, error) as BOOL
    }
    
    fun moveItemAtURL_toURL_error(srcURL: MemorySegment, dstURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("moveItemAtURL:toURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, srcURL, dstURL, error) as BOOL
    }
    
    fun linkItemAtURL_toURL_error(srcURL: MemorySegment, dstURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("linkItemAtURL:toURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, srcURL, dstURL, error) as BOOL
    }
    
    fun removeItemAtURL_error(URL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeItemAtURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, URL, error) as BOOL
    }
    
    fun trashItemAtURL_resultingItemURL_error(url: MemorySegment, outResultingURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("trashItemAtURL:resultingItemURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, outResultingURL, error) as BOOL
    }
    
    fun fileAttributesAtPath_traverseLink(path: MemorySegment, yorn: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("fileAttributesAtPath:traverseLink:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, yorn) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun fileAttributesAtPath_traverseLink(path: String, yorn: BOOL): MemorySegment = fileAttributesAtPath_traverseLink(ObjCRuntime.newNSString(Arena.global(), path), yorn)
    
    fun changeFileAttributes_atPath(attributes: MemorySegment, path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("changeFileAttributes:atPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, attributes, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun changeFileAttributes_atPath(attributes: MemorySegment, path: String): BOOL = changeFileAttributes_atPath(attributes, ObjCRuntime.newNSString(Arena.global(), path))
    
    fun directoryContentsAtPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("directoryContentsAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun directoryContentsAtPath(path: String): MemorySegment = directoryContentsAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun fileSystemAttributesAtPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fileSystemAttributesAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun fileSystemAttributesAtPath(path: String): MemorySegment = fileSystemAttributesAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun pathContentOfSymbolicLinkAtPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("pathContentOfSymbolicLinkAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathContentOfSymbolicLinkAtPathAsString(path: MemorySegment): String = ObjCRuntime.toJavaString(pathContentOfSymbolicLinkAtPath(path))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun pathContentOfSymbolicLinkAtPath(path: String): MemorySegment = pathContentOfSymbolicLinkAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun pathContentOfSymbolicLinkAtPathAsString(path: String): String = ObjCRuntime.toJavaString(pathContentOfSymbolicLinkAtPath(ObjCRuntime.newNSString(Arena.global(), path)))
    
    fun createSymbolicLinkAtPath_pathContent(path: MemorySegment, otherpath: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("createSymbolicLinkAtPath:pathContent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, otherpath) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun createSymbolicLinkAtPath_pathContent(path: String, otherpath: String): BOOL = createSymbolicLinkAtPath_pathContent(ObjCRuntime.newNSString(Arena.global(), path), ObjCRuntime.newNSString(Arena.global(), otherpath))
    
    fun createDirectoryAtPath_attributes(path: MemorySegment, attributes: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("createDirectoryAtPath:attributes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, attributes) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun createDirectoryAtPath_attributes(path: String, attributes: MemorySegment): BOOL = createDirectoryAtPath_attributes(ObjCRuntime.newNSString(Arena.global(), path), attributes)
    
    fun linkPath_toPath_handler(src: MemorySegment, dest: MemorySegment, handler: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("linkPath:toPath:handler:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, src, dest, handler) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun linkPath_toPath_handler(src: String, dest: String, handler: MemorySegment): BOOL = linkPath_toPath_handler(ObjCRuntime.newNSString(Arena.global(), src), ObjCRuntime.newNSString(Arena.global(), dest), handler)
    
    fun copyPath_toPath_handler(src: MemorySegment, dest: MemorySegment, handler: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("copyPath:toPath:handler:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, src, dest, handler) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun copyPath_toPath_handler(src: String, dest: String, handler: MemorySegment): BOOL = copyPath_toPath_handler(ObjCRuntime.newNSString(Arena.global(), src), ObjCRuntime.newNSString(Arena.global(), dest), handler)
    
    fun movePath_toPath_handler(src: MemorySegment, dest: MemorySegment, handler: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("movePath:toPath:handler:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, src, dest, handler) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun movePath_toPath_handler(src: String, dest: String, handler: MemorySegment): BOOL = movePath_toPath_handler(ObjCRuntime.newNSString(Arena.global(), src), ObjCRuntime.newNSString(Arena.global(), dest), handler)
    
    fun removeFileAtPath_handler(path: MemorySegment, handler: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeFileAtPath:handler:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, handler) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun removeFileAtPath_handler(path: String, handler: MemorySegment): BOOL = removeFileAtPath_handler(ObjCRuntime.newNSString(Arena.global(), path), handler)
    
    fun changeCurrentDirectoryPath(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("changeCurrentDirectoryPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun changeCurrentDirectoryPath(path: String): BOOL = changeCurrentDirectoryPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun fileExistsAtPath(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("fileExistsAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun fileExistsAtPath(path: String): BOOL = fileExistsAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun fileExistsAtPath_isDirectory(path: MemorySegment, isDirectory: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("fileExistsAtPath:isDirectory:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, isDirectory) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun fileExistsAtPath_isDirectory(path: String, isDirectory: MemorySegment): BOOL = fileExistsAtPath_isDirectory(ObjCRuntime.newNSString(Arena.global(), path), isDirectory)
    
    fun isReadableFileAtPath(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isReadableFileAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun isReadableFileAtPath(path: String): BOOL = isReadableFileAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun isWritableFileAtPath(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isWritableFileAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun isWritableFileAtPath(path: String): BOOL = isWritableFileAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun isExecutableFileAtPath(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isExecutableFileAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun isExecutableFileAtPath(path: String): BOOL = isExecutableFileAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun isDeletableFileAtPath(path: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isDeletableFileAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun isDeletableFileAtPath(path: String): BOOL = isDeletableFileAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun contentsEqualAtPath_andPath(path1: MemorySegment, path2: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("contentsEqualAtPath:andPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path1, path2) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun contentsEqualAtPath_andPath(path1: String, path2: String): BOOL = contentsEqualAtPath_andPath(ObjCRuntime.newNSString(Arena.global(), path1), ObjCRuntime.newNSString(Arena.global(), path2))
    
    fun displayNameAtPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("displayNameAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun displayNameAtPathAsString(path: MemorySegment): String = ObjCRuntime.toJavaString(displayNameAtPath(path))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun displayNameAtPath(path: String): MemorySegment = displayNameAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun displayNameAtPathAsString(path: String): String = ObjCRuntime.toJavaString(displayNameAtPath(ObjCRuntime.newNSString(Arena.global(), path)))
    
    /** @return NSArray<NSString *> * */
    fun componentsToDisplayForPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("componentsToDisplayForPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun componentsToDisplayForPath(path: String): MemorySegment = componentsToDisplayForPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    /** @return NSDirectoryEnumerator<NSString *> * */
    fun enumeratorAtPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("enumeratorAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun enumeratorAtPath(path: String): MemorySegment = enumeratorAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    /** @return NSDirectoryEnumerator<NSURL *> * */
    fun enumeratorAtURL_includingPropertiesForKeys_options_errorHandler(url: MemorySegment, keys: MemorySegment, mask: NSDirectoryEnumerationOptions, handler: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("enumeratorAtURL:includingPropertiesForKeys:options:errorHandler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, keys, mask, handler) as MemorySegment
    }
    
    /** @return NSArray<NSString *> * */
    fun subpathsAtPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("subpathsAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun subpathsAtPath(path: String): MemorySegment = subpathsAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun contentsAtPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("contentsAtPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun contentsAtPath(path: String): MemorySegment = contentsAtPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun createFileAtPath_contents_attributes(path: MemorySegment, `data`: MemorySegment, attr: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("createFileAtPath:contents:attributes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, `data`, attr) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun createFileAtPath_contents_attributes(path: String, `data`: MemorySegment, attr: MemorySegment): BOOL = createFileAtPath_contents_attributes(ObjCRuntime.newNSString(Arena.global(), path), `data`, attr)
    
    fun fileSystemRepresentationWithPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fileSystemRepresentationWithPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun fileSystemRepresentationWithPath(path: String): MemorySegment = fileSystemRepresentationWithPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun stringWithFileSystemRepresentation_length(str: MemorySegment, len: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("stringWithFileSystemRepresentation:length:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, str, len) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringWithFileSystemRepresentation_lengthAsString(str: MemorySegment, len: NSUInteger): String = ObjCRuntime.toJavaString(stringWithFileSystemRepresentation_length(str, len))
    
    fun replaceItemAtURL_withItemAtURL_backupItemName_options_resultingItemURL_error(originalItemURL: MemorySegment, newItemURL: MemorySegment, backupItemName: MemorySegment, options: NSFileManagerItemReplacementOptions, resultingURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("replaceItemAtURL:withItemAtURL:backupItemName:options:resultingItemURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, originalItemURL, newItemURL, backupItemName, options, resultingURL, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun replaceItemAtURL_withItemAtURL_backupItemName_options_resultingItemURL_error(originalItemURL: MemorySegment, newItemURL: MemorySegment, backupItemName: String, options: NSFileManagerItemReplacementOptions, resultingURL: MemorySegment, error: MemorySegment): BOOL = replaceItemAtURL_withItemAtURL_backupItemName_options_resultingItemURL_error(originalItemURL, newItemURL, ObjCRuntime.newNSString(Arena.global(), backupItemName), options, resultingURL, error)
    
    fun setUbiquitous_itemAtURL_destinationURL_error(flag: BOOL, url: MemorySegment, destinationURL: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setUbiquitous:itemAtURL:destinationURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, flag, url, destinationURL, error) as BOOL
    }
    
    fun isUbiquitousItemAtURL(url: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isUbiquitousItemAtURL:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url) as BOOL
    }
    
    fun startDownloadingUbiquitousItemAtURL_error(url: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("startDownloadingUbiquitousItemAtURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, error) as BOOL
    }
    
    fun evictUbiquitousItemAtURL_error(url: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("evictUbiquitousItemAtURL:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, error) as BOOL
    }
    
    fun URLForUbiquityContainerIdentifier(containerIdentifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForUbiquityContainerIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, containerIdentifier) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun URLForUbiquityContainerIdentifier(containerIdentifier: String): MemorySegment = URLForUbiquityContainerIdentifier(ObjCRuntime.newNSString(Arena.global(), containerIdentifier))
    
    fun URLForPublishingUbiquitousItemAtURL_expirationDate_error(url: MemorySegment, outDate: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForPublishingUbiquitousItemAtURL:expirationDate:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, outDate, error) as MemorySegment
    }
    
    fun pauseSyncForUbiquitousItemAtURL_completionHandler(url: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("pauseSyncForUbiquitousItemAtURL:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, url, completionHandler)
    }
    
    fun resumeSyncForUbiquitousItemAtURL_withBehavior_completionHandler(url: MemorySegment, behavior: NSFileManagerResumeSyncBehavior, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("resumeSyncForUbiquitousItemAtURL:withBehavior:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, url, behavior, completionHandler)
    }
    
    fun fetchLatestRemoteVersionOfItemAtURL_completionHandler(url: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("fetchLatestRemoteVersionOfItemAtURL:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, url, completionHandler)
    }
    
    fun uploadLocalVersionOfUbiquitousItemAtURL_withConflictResolutionPolicy_completionHandler(url: MemorySegment, conflictResolutionPolicy: NSFileManagerUploadLocalVersionConflictPolicy, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("uploadLocalVersionOfUbiquitousItemAtURL:withConflictResolutionPolicy:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, url, conflictResolutionPolicy, completionHandler)
    }
    
    fun getFileProviderServicesForItemAtURL_completionHandler(url: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getFileProviderServicesForItemAtURL:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, url, completionHandler)
    }
    
    fun containerURLForSecurityApplicationGroupIdentifier(groupIdentifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("containerURLForSecurityApplicationGroupIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, groupIdentifier) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun containerURLForSecurityApplicationGroupIdentifier(groupIdentifier: String): MemorySegment = containerURLForSecurityApplicationGroupIdentifier(ObjCRuntime.newNSString(Arena.global(), groupIdentifier))
    
    // @property defaultManager
    fun defaultManager(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property delegate
    /** @return id<NSFileManagerDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentDirectoryPath
    fun currentDirectoryPath(): MemorySegment {
        val sel = ObjCRuntime.sel("currentDirectoryPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun currentDirectoryPathAsString(): String = ObjCRuntime.toJavaString(currentDirectoryPath())
    
    // @property ubiquityIdentityToken
    /** @return id<NSObject,NSCopying,NSCoding> */
    fun ubiquityIdentityToken(): MemorySegment {
        val sel = ObjCRuntime.sel("ubiquityIdentityToken")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSUserInformation on NSFileManager ─────────────────────────────────────────

fun NSFileManager.homeDirectoryForUser(userName: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("homeDirectoryForUser:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, userName) as MemorySegment
}

fun NSFileManager.homeDirectoryForCurrentUser(): MemorySegment {
    val sel = ObjCRuntime.sel("homeDirectoryForCurrentUser")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSFileManager.temporaryDirectory(): MemorySegment {
    val sel = ObjCRuntime.sel("temporaryDirectory")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property homeDirectoryForCurrentUser
fun NSFileManager.homeDirectoryForCurrentUser(): MemorySegment {
    val sel = ObjCRuntime.sel("homeDirectoryForCurrentUser")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property temporaryDirectory
fun NSFileManager.temporaryDirectory(): MemorySegment {
    val sel = ObjCRuntime.sel("temporaryDirectory")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSWorkspaceAuthorization on NSFileManager ─────────────────────────────────────────

// Class method: +[NSFileManager fileManagerWithAuthorization:]
fun NSFileManager_fileManagerWithAuthorization(authorization: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("fileManagerWithAuthorization:")
    val cls = ObjCRuntime.getClass("NSFileManager")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, authorization) as MemorySegment
}


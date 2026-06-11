/**
 * Kotlin/JVM wrapper for Objective-C class: NSBundle
 * Superclass: NSObject
 */
open class NSBundle(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSBundle") }
        
        fun bundleWithPath(path: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("bundleWithPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun bundleWithPath(path: String): MemorySegment = bundleWithPath(ObjCRuntime.newNSString(Arena.global(), path))
        
        fun bundleWithURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("bundleWithURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        fun bundleForClass(aClass: Class): MemorySegment {
            val sel = ObjCRuntime.sel("bundleForClass:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, aClass) as MemorySegment
        }
        
        fun bundleWithIdentifier(identifier: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("bundleWithIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun bundleWithIdentifier(identifier: String): MemorySegment = bundleWithIdentifier(ObjCRuntime.newNSString(Arena.global(), identifier))
        
        fun URLForResource_withExtension_subdirectory_inBundleWithURL(name: MemorySegment, ext: MemorySegment, subpath: MemorySegment, bundleURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("URLForResource:withExtension:subdirectory:inBundleWithURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, ext, subpath, bundleURL) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun URLForResource_withExtension_subdirectory_inBundleWithURL(name: String, ext: String, subpath: String, bundleURL: MemorySegment): MemorySegment = URLForResource_withExtension_subdirectory_inBundleWithURL(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath), bundleURL)
        
        /** @return NSArray<NSURL *> * */
        fun URLsForResourcesWithExtension_subdirectory_inBundleWithURL(ext: MemorySegment, subpath: MemorySegment, bundleURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("URLsForResourcesWithExtension:subdirectory:inBundleWithURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ext, subpath, bundleURL) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun URLsForResourcesWithExtension_subdirectory_inBundleWithURL(ext: String, subpath: String, bundleURL: MemorySegment): MemorySegment = URLsForResourcesWithExtension_subdirectory_inBundleWithURL(ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath), bundleURL)
        
        fun pathForResource_ofType_inDirectory(name: MemorySegment, ext: MemorySegment, bundlePath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("pathForResource:ofType:inDirectory:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, ext, bundlePath) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun pathForResource_ofType_inDirectoryAsString(name: MemorySegment, ext: MemorySegment, bundlePath: MemorySegment): String = ObjCRuntime.toJavaString(pathForResource_ofType_inDirectory(name, ext, bundlePath))
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun pathForResource_ofType_inDirectory(name: String, ext: String, bundlePath: String): MemorySegment = pathForResource_ofType_inDirectory(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), bundlePath))
        
        /** Convenience overload — [String] parameters and [String] return type. */
        fun pathForResource_ofType_inDirectoryAsString(name: String, ext: String, bundlePath: String): String = ObjCRuntime.toJavaString(pathForResource_ofType_inDirectory(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), bundlePath)))
        
        /** @return NSArray<NSString *> * */
        fun pathsForResourcesOfType_inDirectory(ext: MemorySegment, bundlePath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("pathsForResourcesOfType:inDirectory:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, ext, bundlePath) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun pathsForResourcesOfType_inDirectory(ext: String, bundlePath: String): MemorySegment = pathsForResourcesOfType_inDirectory(ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), bundlePath))
        
        /** @return NSArray<NSString *> * */
        fun preferredLocalizationsFromArray(localizationsArray: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("preferredLocalizationsFromArray:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, localizationsArray) as MemorySegment
        }
        
        /** @return NSArray<NSString *> * */
        fun preferredLocalizationsFromArray_forPreferences(localizationsArray: MemorySegment, preferencesArray: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("preferredLocalizationsFromArray:forPreferences:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, localizationsArray, preferencesArray) as MemorySegment
        }
        
        fun mainBundle(): MemorySegment {
            val sel = ObjCRuntime.sel("mainBundle")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSBundle *> * */
        fun allBundles(): MemorySegment {
            val sel = ObjCRuntime.sel("allBundles")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSBundle *> * */
        fun allFrameworks(): MemorySegment {
            val sel = ObjCRuntime.sel("allFrameworks")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithPath(path: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithPath(path: String): MemorySegment = initWithPath(ObjCRuntime.newNSString(Arena.global(), path))
    
    fun initWithURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    fun load(): BOOL {
        val sel = ObjCRuntime.sel("load")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun unload(): BOOL {
        val sel = ObjCRuntime.sel("unload")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun preflightAndReturnError(error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("preflightAndReturnError:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
    }
    
    fun loadAndReturnError(error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("loadAndReturnError:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
    }
    
    fun URLForAuxiliaryExecutable(executableName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForAuxiliaryExecutable:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, executableName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun URLForAuxiliaryExecutable(executableName: String): MemorySegment = URLForAuxiliaryExecutable(ObjCRuntime.newNSString(Arena.global(), executableName))
    
    fun pathForAuxiliaryExecutable(executableName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("pathForAuxiliaryExecutable:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, executableName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathForAuxiliaryExecutableAsString(executableName: MemorySegment): String = ObjCRuntime.toJavaString(pathForAuxiliaryExecutable(executableName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun pathForAuxiliaryExecutable(executableName: String): MemorySegment = pathForAuxiliaryExecutable(ObjCRuntime.newNSString(Arena.global(), executableName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun pathForAuxiliaryExecutableAsString(executableName: String): String = ObjCRuntime.toJavaString(pathForAuxiliaryExecutable(ObjCRuntime.newNSString(Arena.global(), executableName)))
    
    fun URLForResource_withExtension(name: MemorySegment, ext: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForResource:withExtension:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, ext) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun URLForResource_withExtension(name: String, ext: String): MemorySegment = URLForResource_withExtension(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext))
    
    fun URLForResource_withExtension_subdirectory(name: MemorySegment, ext: MemorySegment, subpath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForResource:withExtension:subdirectory:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, ext, subpath) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun URLForResource_withExtension_subdirectory(name: String, ext: String, subpath: String): MemorySegment = URLForResource_withExtension_subdirectory(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath))
    
    fun URLForResource_withExtension_subdirectory_localization(name: MemorySegment, ext: MemorySegment, subpath: MemorySegment, localizationName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForResource:withExtension:subdirectory:localization:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, ext, subpath, localizationName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun URLForResource_withExtension_subdirectory_localization(name: String, ext: String, subpath: String, localizationName: String): MemorySegment = URLForResource_withExtension_subdirectory_localization(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath), ObjCRuntime.newNSString(Arena.global(), localizationName))
    
    /** @return NSArray<NSURL *> * */
    fun URLsForResourcesWithExtension_subdirectory(ext: MemorySegment, subpath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLsForResourcesWithExtension:subdirectory:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ext, subpath) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun URLsForResourcesWithExtension_subdirectory(ext: String, subpath: String): MemorySegment = URLsForResourcesWithExtension_subdirectory(ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath))
    
    /** @return NSArray<NSURL *> * */
    fun URLsForResourcesWithExtension_subdirectory_localization(ext: MemorySegment, subpath: MemorySegment, localizationName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLsForResourcesWithExtension:subdirectory:localization:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ext, subpath, localizationName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun URLsForResourcesWithExtension_subdirectory_localization(ext: String, subpath: String, localizationName: String): MemorySegment = URLsForResourcesWithExtension_subdirectory_localization(ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath), ObjCRuntime.newNSString(Arena.global(), localizationName))
    
    fun pathForResource_ofType(name: MemorySegment, ext: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("pathForResource:ofType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, ext) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathForResource_ofTypeAsString(name: MemorySegment, ext: MemorySegment): String = ObjCRuntime.toJavaString(pathForResource_ofType(name, ext))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun pathForResource_ofType(name: String, ext: String): MemorySegment = pathForResource_ofType(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun pathForResource_ofTypeAsString(name: String, ext: String): String = ObjCRuntime.toJavaString(pathForResource_ofType(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext)))
    
    fun pathForResource_ofType_inDirectory(name: MemorySegment, ext: MemorySegment, subpath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("pathForResource:ofType:inDirectory:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, ext, subpath) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathForResource_ofType_inDirectoryAsString(name: MemorySegment, ext: MemorySegment, subpath: MemorySegment): String = ObjCRuntime.toJavaString(pathForResource_ofType_inDirectory(name, ext, subpath))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun pathForResource_ofType_inDirectory(name: String, ext: String, subpath: String): MemorySegment = pathForResource_ofType_inDirectory(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun pathForResource_ofType_inDirectoryAsString(name: String, ext: String, subpath: String): String = ObjCRuntime.toJavaString(pathForResource_ofType_inDirectory(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath)))
    
    fun pathForResource_ofType_inDirectory_forLocalization(name: MemorySegment, ext: MemorySegment, subpath: MemorySegment, localizationName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("pathForResource:ofType:inDirectory:forLocalization:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, ext, subpath, localizationName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathForResource_ofType_inDirectory_forLocalizationAsString(name: MemorySegment, ext: MemorySegment, subpath: MemorySegment, localizationName: MemorySegment): String = ObjCRuntime.toJavaString(pathForResource_ofType_inDirectory_forLocalization(name, ext, subpath, localizationName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun pathForResource_ofType_inDirectory_forLocalization(name: String, ext: String, subpath: String, localizationName: String): MemorySegment = pathForResource_ofType_inDirectory_forLocalization(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath), ObjCRuntime.newNSString(Arena.global(), localizationName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun pathForResource_ofType_inDirectory_forLocalizationAsString(name: String, ext: String, subpath: String, localizationName: String): String = ObjCRuntime.toJavaString(pathForResource_ofType_inDirectory_forLocalization(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath), ObjCRuntime.newNSString(Arena.global(), localizationName)))
    
    /** @return NSArray<NSString *> * */
    fun pathsForResourcesOfType_inDirectory(ext: MemorySegment, subpath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("pathsForResourcesOfType:inDirectory:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ext, subpath) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun pathsForResourcesOfType_inDirectory(ext: String, subpath: String): MemorySegment = pathsForResourcesOfType_inDirectory(ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath))
    
    /** @return NSArray<NSString *> * */
    fun pathsForResourcesOfType_inDirectory_forLocalization(ext: MemorySegment, subpath: MemorySegment, localizationName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("pathsForResourcesOfType:inDirectory:forLocalization:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ext, subpath, localizationName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun pathsForResourcesOfType_inDirectory_forLocalization(ext: String, subpath: String, localizationName: String): MemorySegment = pathsForResourcesOfType_inDirectory_forLocalization(ObjCRuntime.newNSString(Arena.global(), ext), ObjCRuntime.newNSString(Arena.global(), subpath), ObjCRuntime.newNSString(Arena.global(), localizationName))
    
    fun localizedStringForKey_value_table(key: MemorySegment, value: MemorySegment, tableName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("localizedStringForKey:value:table:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, value, tableName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedStringForKey_value_tableAsString(key: MemorySegment, value: MemorySegment, tableName: MemorySegment): String = ObjCRuntime.toJavaString(localizedStringForKey_value_table(key, value, tableName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun localizedStringForKey_value_table(key: String, value: String, tableName: String): MemorySegment = localizedStringForKey_value_table(ObjCRuntime.newNSString(Arena.global(), key), ObjCRuntime.newNSString(Arena.global(), value), ObjCRuntime.newNSString(Arena.global(), tableName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun localizedStringForKey_value_tableAsString(key: String, value: String, tableName: String): String = ObjCRuntime.toJavaString(localizedStringForKey_value_table(ObjCRuntime.newNSString(Arena.global(), key), ObjCRuntime.newNSString(Arena.global(), value), ObjCRuntime.newNSString(Arena.global(), tableName)))
    
    fun localizedAttributedStringForKey_value_table(key: MemorySegment, value: MemorySegment, tableName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("localizedAttributedStringForKey:value:table:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, value, tableName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun localizedAttributedStringForKey_value_table(key: String, value: String, tableName: String): MemorySegment = localizedAttributedStringForKey_value_table(ObjCRuntime.newNSString(Arena.global(), key), ObjCRuntime.newNSString(Arena.global(), value), ObjCRuntime.newNSString(Arena.global(), tableName))
    
    fun localizedStringForKey_value_table_localizations(key: MemorySegment, value: MemorySegment, tableName: MemorySegment, localizations: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("localizedStringForKey:value:table:localizations:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, value, tableName, localizations) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedStringForKey_value_table_localizationsAsString(key: MemorySegment, value: MemorySegment, tableName: MemorySegment, localizations: MemorySegment): String = ObjCRuntime.toJavaString(localizedStringForKey_value_table_localizations(key, value, tableName, localizations))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun localizedStringForKey_value_table_localizations(key: String, value: String, tableName: String, localizations: MemorySegment): MemorySegment = localizedStringForKey_value_table_localizations(ObjCRuntime.newNSString(Arena.global(), key), ObjCRuntime.newNSString(Arena.global(), value), ObjCRuntime.newNSString(Arena.global(), tableName), localizations)
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun localizedStringForKey_value_table_localizationsAsString(key: String, value: String, tableName: String, localizations: MemorySegment): String = ObjCRuntime.toJavaString(localizedStringForKey_value_table_localizations(ObjCRuntime.newNSString(Arena.global(), key), ObjCRuntime.newNSString(Arena.global(), value), ObjCRuntime.newNSString(Arena.global(), tableName), localizations))
    
    fun objectForInfoDictionaryKey(key: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectForInfoDictionaryKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun objectForInfoDictionaryKey(key: String): MemorySegment = objectForInfoDictionaryKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    fun classNamed(className: MemorySegment): Class {
        val sel = ObjCRuntime.sel("classNamed:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, className) as Class
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun classNamed(className: String): Class = classNamed(ObjCRuntime.newNSString(Arena.global(), className))
    
    // @property mainBundle
    fun mainBundle(): MemorySegment {
        val sel = ObjCRuntime.sel("mainBundle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property allBundles
    /** @return NSArray<NSBundle *> * */
    fun allBundles(): MemorySegment {
        val sel = ObjCRuntime.sel("allBundles")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property allFrameworks
    /** @return NSArray<NSBundle *> * */
    fun allFrameworks(): MemorySegment {
        val sel = ObjCRuntime.sel("allFrameworks")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property loaded
    fun isLoaded(): BOOL {
        val sel = ObjCRuntime.sel("isLoaded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property bundleURL
    fun bundleURL(): MemorySegment {
        val sel = ObjCRuntime.sel("bundleURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property resourceURL
    fun resourceURL(): MemorySegment {
        val sel = ObjCRuntime.sel("resourceURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property executableURL
    fun executableURL(): MemorySegment {
        val sel = ObjCRuntime.sel("executableURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property privateFrameworksURL
    fun privateFrameworksURL(): MemorySegment {
        val sel = ObjCRuntime.sel("privateFrameworksURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sharedFrameworksURL
    fun sharedFrameworksURL(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedFrameworksURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sharedSupportURL
    fun sharedSupportURL(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedSupportURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property builtInPlugInsURL
    fun builtInPlugInsURL(): MemorySegment {
        val sel = ObjCRuntime.sel("builtInPlugInsURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property appStoreReceiptURL
    fun appStoreReceiptURL(): MemorySegment {
        val sel = ObjCRuntime.sel("appStoreReceiptURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bundlePath
    fun bundlePath(): MemorySegment {
        val sel = ObjCRuntime.sel("bundlePath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun bundlePathAsString(): String = ObjCRuntime.toJavaString(bundlePath())
    
    // @property resourcePath
    fun resourcePath(): MemorySegment {
        val sel = ObjCRuntime.sel("resourcePath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun resourcePathAsString(): String = ObjCRuntime.toJavaString(resourcePath())
    
    // @property executablePath
    fun executablePath(): MemorySegment {
        val sel = ObjCRuntime.sel("executablePath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun executablePathAsString(): String = ObjCRuntime.toJavaString(executablePath())
    
    // @property privateFrameworksPath
    fun privateFrameworksPath(): MemorySegment {
        val sel = ObjCRuntime.sel("privateFrameworksPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun privateFrameworksPathAsString(): String = ObjCRuntime.toJavaString(privateFrameworksPath())
    
    // @property sharedFrameworksPath
    fun sharedFrameworksPath(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedFrameworksPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun sharedFrameworksPathAsString(): String = ObjCRuntime.toJavaString(sharedFrameworksPath())
    
    // @property sharedSupportPath
    fun sharedSupportPath(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedSupportPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun sharedSupportPathAsString(): String = ObjCRuntime.toJavaString(sharedSupportPath())
    
    // @property builtInPlugInsPath
    fun builtInPlugInsPath(): MemorySegment {
        val sel = ObjCRuntime.sel("builtInPlugInsPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun builtInPlugInsPathAsString(): String = ObjCRuntime.toJavaString(builtInPlugInsPath())
    
    // @property bundleIdentifier
    fun bundleIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("bundleIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun bundleIdentifierAsString(): String = ObjCRuntime.toJavaString(bundleIdentifier())
    
    // @property infoDictionary
    /** @return NSDictionary<NSString *,id> * */
    fun infoDictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("infoDictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localizedInfoDictionary
    /** @return NSDictionary<NSString *,id> * */
    fun localizedInfoDictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedInfoDictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property principalClass
    fun principalClass(): Class {
        val sel = ObjCRuntime.sel("principalClass")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
    }
    
    // @property preferredLocalizations
    /** @return NSArray<NSString *> * */
    fun preferredLocalizations(): MemorySegment {
        val sel = ObjCRuntime.sel("preferredLocalizations")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localizations
    /** @return NSArray<NSString *> * */
    fun localizations(): MemorySegment {
        val sel = ObjCRuntime.sel("localizations")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property developmentLocalization
    fun developmentLocalization(): MemorySegment {
        val sel = ObjCRuntime.sel("developmentLocalization")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun developmentLocalizationAsString(): String = ObjCRuntime.toJavaString(developmentLocalization())
    
    // @property executableArchitectures
    /** @return NSArray<NSNumber *> * */
    fun executableArchitectures(): MemorySegment {
        val sel = ObjCRuntime.sel("executableArchitectures")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSBundleResourceRequestAdditions on NSBundle ─────────────────────────────────────────

fun NSBundle.setPreservationPriority_forTags(priority: Double, tags: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setPreservationPriority:forTags:")
    ObjCRuntime.msgSend(null, ptr, sel, priority, tags)
}

fun NSBundle.preservationPriorityForTag(tag: MemorySegment): Double {
    val sel = ObjCRuntime.sel("preservationPriorityForTag:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, tag) as Double
}

// ── Category: NSBundleHelpExtension on NSBundle ─────────────────────────────────────────

fun NSBundle.contextHelpForKey(key: NSHelpManagerContextHelpKey): MemorySegment {
    val sel = ObjCRuntime.sel("contextHelpForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

// ── Category: NSBundleImageExtension on NSBundle ─────────────────────────────────────────

fun NSBundle.imageForResource(name: NSImageName): MemorySegment {
    val sel = ObjCRuntime.sel("imageForResource:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
}

fun NSBundle.pathForImageResource(name: NSImageName): MemorySegment {
    val sel = ObjCRuntime.sel("pathForImageResource:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
}

fun NSBundle.URLForImageResource(name: NSImageName): MemorySegment {
    val sel = ObjCRuntime.sel("URLForImageResource:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
}

// ── Category: NSNibLoading on NSBundle ─────────────────────────────────────────

fun NSBundle.loadNibNamed_owner_topLevelObjects(nibName: NSNibName, owner: MemorySegment, topLevelObjects: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("loadNibNamed:owner:topLevelObjects:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, nibName, owner, topLevelObjects) as BOOL
}

// ── Category: NSNibLoadingDeprecated on NSBundle ─────────────────────────────────────────

fun NSBundle.loadNibFile_externalNameTable_withZone(fileName: MemorySegment, context: MemorySegment, zone: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("loadNibFile:externalNameTable:withZone:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fileName, context, zone) as BOOL
}

// Class method: +[NSBundle loadNibFile:externalNameTable:withZone:]
fun NSBundle_loadNibFile_externalNameTable_withZone(fileName: MemorySegment, context: MemorySegment, zone: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("loadNibFile:externalNameTable:withZone:")
    val cls = ObjCRuntime.getClass("NSBundle")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel, fileName, context, zone) as BOOL
}

// Class method: +[NSBundle loadNibNamed:owner:]
fun NSBundle_loadNibNamed_owner(nibName: MemorySegment, owner: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("loadNibNamed:owner:")
    val cls = ObjCRuntime.getClass("NSBundle")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel, nibName, owner) as BOOL
}

// ── Category: NSBundleSoundExtensions on NSBundle ─────────────────────────────────────────

fun NSBundle.pathForSoundResource(name: NSSoundName): MemorySegment {
    val sel = ObjCRuntime.sel("pathForSoundResource:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
}


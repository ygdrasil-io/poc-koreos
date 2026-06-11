/**
 * Kotlin/JVM wrapper for Objective-C class: NSPasteboardItem
 * Superclass: NSObject
 * Protocols: NSPasteboardWriting, NSPasteboardReading
 */
open class NSPasteboardItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPasteboardItem") }
        
    }
    
    fun availableTypeFromArray(types: MemorySegment): NSPasteboardType {
        val sel = ObjCRuntime.sel("availableTypeFromArray:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, types) as NSPasteboardType
    }
    
    fun setDataProvider_forTypes(dataProvider: MemorySegment, types: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setDataProvider:forTypes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, dataProvider, types) as BOOL
    }
    
    fun setData_forType(`data`: MemorySegment, type: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setData:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `data`, type) as BOOL
    }
    
    fun setString_forType(string: MemorySegment, type: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setString:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string, type) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setString_forType(string: String, type: NSPasteboardType): BOOL = setString_forType(ObjCRuntime.newNSString(Arena.global(), string), type)
    
    fun setPropertyList_forType(propertyList: MemorySegment, type: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setPropertyList:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, propertyList, type) as BOOL
    }
    
    fun dataForType(type: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("dataForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
    }
    
    fun stringForType(type: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("stringForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringForTypeAsString(type: NSPasteboardType): String = ObjCRuntime.toJavaString(stringForType(type))
    
    fun propertyListForType(type: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("propertyListForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
    }
    
    fun detectPatternsForPatterns_completionHandler(patterns: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("detectPatternsForPatterns:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, patterns, completionHandler)
    }
    
    fun detectValuesForPatterns_completionHandler(patterns: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("detectValuesForPatterns:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, patterns, completionHandler)
    }
    
    fun detectMetadataForTypes_completionHandler(types: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("detectMetadataForTypes:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, types, completionHandler)
    }
    
    // @property types
    /** @return NSArray<NSPasteboardType> * */
    fun types(): MemorySegment {
        val sel = ObjCRuntime.sel("types")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


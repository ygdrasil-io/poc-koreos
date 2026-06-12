package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPasteboardItem
 * Superclass: NSObject
 * Protocols: NSPasteboardWriting, NSPasteboardReading
 */
open class NSPasteboardItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPasteboardItem") }
        
    }
    
    open fun availableTypeFromArray(types: MemorySegment): NSPasteboardType {
        val sel = ObjCRuntime.sel("availableTypeFromArray:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, types) as NSPasteboardType
    }
    
    open fun setDataProvider_forTypes(dataProvider: MemorySegment, types: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setDataProvider:forTypes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, dataProvider, types) as BOOL
    }
    
    open fun setData_forType(`data`: MemorySegment, type: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setData:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `data`, type) as BOOL
    }
    
    open fun setString_forType(string: MemorySegment, type: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setString:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string, type) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setString_forType(string: String, type: NSPasteboardType): BOOL = setString_forType(ObjCRuntime.newNSString(Arena.global(), string), type)
    
    open fun setPropertyList_forType(propertyList: MemorySegment, type: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setPropertyList:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, propertyList, type) as BOOL
    }
    
    open fun dataForType(type: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("dataForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
    }
    
    open fun stringForType(type: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("stringForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun stringForTypeAsString(type: NSPasteboardType): String = ObjCRuntime.toJavaString(stringForType(type))
    
    open fun propertyListForType(type: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("propertyListForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
    }
    
    open fun detectPatternsForPatterns_completionHandler(patterns: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("detectPatternsForPatterns:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, patterns, completionHandler)
    }
    
    open fun detectValuesForPatterns_completionHandler(patterns: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("detectValuesForPatterns:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, patterns, completionHandler)
    }
    
    open fun detectMetadataForTypes_completionHandler(types: MemorySegment, completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("detectMetadataForTypes:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, types, completionHandler)
    }
    
    // @property types
    /** @return NSArray<NSPasteboardType> * */
    open fun types(): MemorySegment {
        val sel = ObjCRuntime.sel("types")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


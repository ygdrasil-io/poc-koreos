package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPasteboard
 * Superclass: NSObject
 */
open class NSPasteboard(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPasteboard") }
        
        open fun pasteboardWithName(name: NSPasteboardName): MemorySegment {
            val sel = ObjCRuntime.sel("pasteboardWithName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        open fun pasteboardWithUniqueName(): MemorySegment {
            val sel = ObjCRuntime.sel("pasteboardWithUniqueName")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun generalPasteboard(): MemorySegment {
            val sel = ObjCRuntime.sel("generalPasteboard")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun releaseGlobally(): Unit {
        val sel = ObjCRuntime.sel("releaseGlobally")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun prepareForNewContentsWithOptions(options: NSPasteboardContentsOptions): NSInteger {
        val sel = ObjCRuntime.sel("prepareForNewContentsWithOptions:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, options) as NSInteger
    }
    
    open fun clearContents(): NSInteger {
        val sel = ObjCRuntime.sel("clearContents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    open fun writeObjects(objects: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("writeObjects:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, objects) as BOOL
    }
    
    open fun readObjectsForClasses_options(classArray: MemorySegment, options: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("readObjectsForClasses:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, classArray, options) as MemorySegment
    }
    
    open fun indexOfPasteboardItem(pasteboardItem: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("indexOfPasteboardItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, pasteboardItem) as NSUInteger
    }
    
    open fun canReadItemWithDataConformingToTypes(types: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("canReadItemWithDataConformingToTypes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, types) as BOOL
    }
    
    open fun canReadObjectForClasses_options(classArray: MemorySegment, options: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("canReadObjectForClasses:options:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, classArray, options) as BOOL
    }
    
    open fun declareTypes_owner(newTypes: MemorySegment, newOwner: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("declareTypes:owner:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, newTypes, newOwner) as NSInteger
    }
    
    open fun addTypes_owner(newTypes: MemorySegment, newOwner: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("addTypes:owner:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, newTypes, newOwner) as NSInteger
    }
    
    open fun availableTypeFromArray(types: MemorySegment): NSPasteboardType {
        val sel = ObjCRuntime.sel("availableTypeFromArray:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, types) as NSPasteboardType
    }
    
    open fun setData_forType(`data`: MemorySegment, dataType: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setData:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `data`, dataType) as BOOL
    }
    
    open fun setPropertyList_forType(plist: MemorySegment, dataType: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setPropertyList:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, plist, dataType) as BOOL
    }
    
    open fun setString_forType(string: MemorySegment, dataType: NSPasteboardType): BOOL {
        val sel = ObjCRuntime.sel("setString:forType:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string, dataType) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setString_forType(string: String, dataType: NSPasteboardType): BOOL = setString_forType(ObjCRuntime.newNSString(Arena.global(), string), dataType)
    
    open fun dataForType(dataType: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("dataForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dataType) as MemorySegment
    }
    
    open fun propertyListForType(dataType: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("propertyListForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dataType) as MemorySegment
    }
    
    open fun stringForType(dataType: NSPasteboardType): MemorySegment {
        val sel = ObjCRuntime.sel("stringForType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dataType) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun stringForTypeAsString(dataType: NSPasteboardType): String = ObjCRuntime.toJavaString(stringForType(dataType))
    
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
    
    // @property generalPasteboard
    open fun name(): NSPasteboardName {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPasteboardName
    }
    
    // @property changeCount
    open fun changeCount(): NSInteger {
        val sel = ObjCRuntime.sel("changeCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property accessBehavior
    open fun accessBehavior(): NSPasteboardAccessBehavior {
        val sel = ObjCRuntime.sel("accessBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPasteboardAccessBehavior
    }
    
    // @property pasteboardItems
    /** @return NSArray<NSPasteboardItem *> * */
    open fun pasteboardItems(): MemorySegment {
        val sel = ObjCRuntime.sel("pasteboardItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property types
    /** @return NSArray<NSPasteboardType> * */
    open fun types(): MemorySegment {
        val sel = ObjCRuntime.sel("types")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: FilterServices on NSPasteboard ─────────────────────────────────────────

// Class<*> method: +[NSPasteboard typesFilterableTo:]
fun NSPasteboard_typesFilterableTo(type: NSPasteboardType): MemorySegment {
    val sel = ObjCRuntime.sel("typesFilterableTo:")
    val cls = ObjCRuntime.getClass("NSPasteboard")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, type) as MemorySegment
}

// Class<*> method: +[NSPasteboard pasteboardByFilteringFile:]
fun NSPasteboard_pasteboardByFilteringFile(filename: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("pasteboardByFilteringFile:")
    val cls = ObjCRuntime.getClass("NSPasteboard")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, filename) as MemorySegment
}

// Class<*> method: +[NSPasteboard pasteboardByFilteringData:ofType:]
fun NSPasteboard_pasteboardByFilteringData_ofType(`data`: MemorySegment, type: NSPasteboardType): MemorySegment {
    val sel = ObjCRuntime.sel("pasteboardByFilteringData:ofType:")
    val cls = ObjCRuntime.getClass("NSPasteboard")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, `data`, type) as MemorySegment
}

// Class<*> method: +[NSPasteboard pasteboardByFilteringTypesInPasteboard:]
fun NSPasteboard_pasteboardByFilteringTypesInPasteboard(pboard: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("pasteboardByFilteringTypesInPasteboard:")
    val cls = ObjCRuntime.getClass("NSPasteboard")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, pboard) as MemorySegment
}

// ── Category: NSFileContents on NSPasteboard ─────────────────────────────────────────

fun NSPasteboard.writeFileContents(filename: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("writeFileContents:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, filename) as BOOL
}

fun NSPasteboard.readFileContentsType_toFile(type: NSPasteboardType, filename: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("readFileContentsType:toFile:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type, filename) as MemorySegment
}

fun NSPasteboard.writeFileWrapper(wrapper: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("writeFileWrapper:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, wrapper) as BOOL
}

fun NSPasteboard.readFileWrapper(): MemorySegment {
    val sel = ObjCRuntime.sel("readFileWrapper")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}


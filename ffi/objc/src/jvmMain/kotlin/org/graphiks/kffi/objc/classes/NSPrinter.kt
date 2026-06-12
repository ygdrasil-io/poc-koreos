package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPrinter
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSPrinter(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPrinter") }
        
        open fun printerWithName(name: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("printerWithName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun printerWithName(name: String): MemorySegment = printerWithName(ObjCRuntime.newNSString(Arena.global(), name))
        
        open fun printerWithType(type: NSPrinterTypeName): MemorySegment {
            val sel = ObjCRuntime.sel("printerWithType:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, type) as MemorySegment
        }
        
        /** @return NSArray<NSString *> * */
        open fun printerNames(): MemorySegment {
            val sel = ObjCRuntime.sel("printerNames")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSPrinterTypeName> * */
        open fun printerTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("printerTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun pageSizeForPaper(paperName: NSPrinterPaperName): NSSize {
        val sel = ObjCRuntime.sel("pageSizeForPaper:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, paperName) as NSSize
    }
    
    // @property printerNames
    /** @return NSArray<NSString *> * */
    }
    
    // @property printerTypes
    /** @return NSArray<NSPrinterTypeName> * */
    }
    
    // @property name
    open fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    // @property type
    open fun type(): NSPrinterTypeName {
        val sel = ObjCRuntime.sel("type")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrinterTypeName
    }
    
    // @property languageLevel
    open fun languageLevel(): NSInteger {
        val sel = ObjCRuntime.sel("languageLevel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property deviceDescription
    /** @return NSDictionary<NSDeviceDescriptionKey,id> * */
    open fun deviceDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("deviceDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSDeprecated on NSPrinter ─────────────────────────────────────────

fun NSPrinter.statusForTable(tableName: MemorySegment): NSPrinterTableStatus {
    val sel = ObjCRuntime.sel("statusForTable:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tableName) as NSPrinterTableStatus
}

fun NSPrinter.isKey_inTable(key: MemorySegment, table: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isKey:inTable:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key, table) as BOOL
}

fun NSPrinter.booleanForKey_inTable(key: MemorySegment, table: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("booleanForKey:inTable:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key, table) as BOOL
}

fun NSPrinter.floatForKey_inTable(key: MemorySegment, table: MemorySegment): Float {
    val sel = ObjCRuntime.sel("floatForKey:inTable:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, key, table) as Float
}

fun NSPrinter.intForKey_inTable(key: MemorySegment, table: MemorySegment): Int {
    val sel = ObjCRuntime.sel("intForKey:inTable:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, key, table) as Int
}

fun NSPrinter.rectForKey_inTable(key: MemorySegment, table: MemorySegment): NSRect {
    val sel = ObjCRuntime.sel("rectForKey:inTable:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, key, table) as NSRect
}

fun NSPrinter.sizeForKey_inTable(key: MemorySegment, table: MemorySegment): NSSize {
    val sel = ObjCRuntime.sel("sizeForKey:inTable:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, key, table) as NSSize
}

fun NSPrinter.stringForKey_inTable(key: MemorySegment, table: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringForKey:inTable:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, table) as MemorySegment
}

fun NSPrinter.stringListForKey_inTable(key: MemorySegment, table: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("stringListForKey:inTable:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, table) as MemorySegment
}

fun NSPrinter.imageRectForPaper(paperName: MemorySegment): NSRect {
    val sel = ObjCRuntime.sel("imageRectForPaper:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, paperName) as NSRect
}

fun NSPrinter.acceptsBinary(): BOOL {
    val sel = ObjCRuntime.sel("acceptsBinary")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSPrinter.isColor(): BOOL {
    val sel = ObjCRuntime.sel("isColor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSPrinter.isFontAvailable(faceName: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isFontAvailable:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, faceName) as BOOL
}

fun NSPrinter.isOutputStackInReverseOrder(): BOOL {
    val sel = ObjCRuntime.sel("isOutputStackInReverseOrder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSPrinter.domain(): MemorySegment {
    val sel = ObjCRuntime.sel("domain")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSPrinter.host(): MemorySegment {
    val sel = ObjCRuntime.sel("host")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSPrinter.note(): MemorySegment {
    val sel = ObjCRuntime.sel("note")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class<*> method: +[NSPrinter printerWithName:domain:includeUnavailable:]
fun NSPrinter_printerWithName_domain_includeUnavailable(name: MemorySegment, domain: MemorySegment, flag: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("printerWithName:domain:includeUnavailable:")
    val cls = ObjCRuntime.getClass("NSPrinter")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, name, domain, flag) as MemorySegment
}


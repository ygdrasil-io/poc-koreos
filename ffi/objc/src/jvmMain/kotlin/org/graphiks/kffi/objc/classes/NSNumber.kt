package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSNumber
 * Superclass: NSValue
 */
open class NSNumber(ptr: MemorySegment) : NSValue(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNumber") }
        
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun initWithChar(value: Byte): MemorySegment {
        val sel = ObjCRuntime.sel("initWithChar:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithUnsignedChar(value: Any): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUnsignedChar:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithShort(value: Short): MemorySegment {
        val sel = ObjCRuntime.sel("initWithShort:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithUnsignedShort(value: Any): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUnsignedShort:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithInt(value: Int): MemorySegment {
        val sel = ObjCRuntime.sel("initWithInt:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithUnsignedInt(value: Any): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUnsignedInt:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithLong(value: Long): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLong:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithUnsignedLong(value: Any): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUnsignedLong:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithLongLong(value: Long): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLongLong:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithUnsignedLongLong(value: Any): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUnsignedLongLong:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithFloat(value: Float): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFloat:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithDouble(value: Double): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDouble:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithBool(value: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithBool:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithInteger(value: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithInteger:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun initWithUnsignedInteger(value: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUnsignedInteger:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun compare(otherNumber: MemorySegment): NSComparisonResult {
        val sel = ObjCRuntime.sel("compare:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherNumber) as NSComparisonResult
    }
    
    fun isEqualToNumber(number: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEqualToNumber:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, number) as BOOL
    }
    
    fun descriptionWithLocale(locale: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("descriptionWithLocale:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun descriptionWithLocaleAsString(locale: MemorySegment): String = ObjCRuntime.toJavaString(descriptionWithLocale(locale))
    
    // @property charValue
    fun charValue(): Byte {
        val sel = ObjCRuntime.sel("charValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BYTE, ptr, sel) as Byte
    }
    
    // @property unsignedCharValue
    fun unsignedCharValue(): Any {
        val sel = ObjCRuntime.sel("unsignedCharValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BYTE, ptr, sel) as Any
    }
    
    // @property shortValue
    fun shortValue(): Short {
        val sel = ObjCRuntime.sel("shortValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel) as Short
    }
    
    // @property unsignedShortValue
    fun unsignedShortValue(): Any {
        val sel = ObjCRuntime.sel("unsignedShortValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel) as Any
    }
    
    // @property intValue
    fun intValue(): Int {
        val sel = ObjCRuntime.sel("intValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    
    // @property unsignedIntValue
    fun unsignedIntValue(): Any {
        val sel = ObjCRuntime.sel("unsignedIntValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Any
    }
    
    // @property longValue
    fun longValue(): Long {
        val sel = ObjCRuntime.sel("longValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Long
    }
    
    // @property unsignedLongValue
    fun unsignedLongValue(): Any {
        val sel = ObjCRuntime.sel("unsignedLongValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Any
    }
    
    // @property longLongValue
    fun longLongValue(): Long {
        val sel = ObjCRuntime.sel("longLongValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Long
    }
    
    // @property unsignedLongLongValue
    fun unsignedLongLongValue(): Any {
        val sel = ObjCRuntime.sel("unsignedLongLongValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Any
    }
    
    // @property floatValue
    fun floatValue(): Float {
        val sel = ObjCRuntime.sel("floatValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    
    // @property doubleValue
    fun doubleValue(): Double {
        val sel = ObjCRuntime.sel("doubleValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    
    // @property boolValue
    fun boolValue(): BOOL {
        val sel = ObjCRuntime.sel("boolValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property integerValue
    fun integerValue(): NSInteger {
        val sel = ObjCRuntime.sel("integerValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property unsignedIntegerValue
    fun unsignedIntegerValue(): NSUInteger {
        val sel = ObjCRuntime.sel("unsignedIntegerValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property stringValue
    fun stringValue(): MemorySegment {
        val sel = ObjCRuntime.sel("stringValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringValueAsString(): String = ObjCRuntime.toJavaString(stringValue())
    
}

// ── Category: NSNumberCreation on NSNumber ─────────────────────────────────────────

// Class<*> method: +[NSNumber numberWithChar:]
fun NSNumber_numberWithChar(value: Byte): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithChar:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithUnsignedChar:]
fun NSNumber_numberWithUnsignedChar(value: Any): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithUnsignedChar:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithShort:]
fun NSNumber_numberWithShort(value: Short): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithShort:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithUnsignedShort:]
fun NSNumber_numberWithUnsignedShort(value: Any): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithUnsignedShort:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithInt:]
fun NSNumber_numberWithInt(value: Int): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithInt:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithUnsignedInt:]
fun NSNumber_numberWithUnsignedInt(value: Any): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithUnsignedInt:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithLong:]
fun NSNumber_numberWithLong(value: Long): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithLong:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithUnsignedLong:]
fun NSNumber_numberWithUnsignedLong(value: Any): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithUnsignedLong:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithLongLong:]
fun NSNumber_numberWithLongLong(value: Long): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithLongLong:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithUnsignedLongLong:]
fun NSNumber_numberWithUnsignedLongLong(value: Any): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithUnsignedLongLong:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithFloat:]
fun NSNumber_numberWithFloat(value: Float): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithFloat:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithDouble:]
fun NSNumber_numberWithDouble(value: Double): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithDouble:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithBool:]
fun NSNumber_numberWithBool(value: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithBool:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithInteger:]
fun NSNumber_numberWithInteger(value: NSInteger): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithInteger:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// Class<*> method: +[NSNumber numberWithUnsignedInteger:]
fun NSNumber_numberWithUnsignedInteger(value: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("numberWithUnsignedInteger:")
    val cls = ObjCRuntime.getClass("NSNumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, value) as MemorySegment
}

// ── Category: NSDecimalNumberExtensions on NSNumber ─────────────────────────────────────────

fun NSNumber.decimalValue(): NSDecimal {
    val sel = ObjCRuntime.sel("decimalValue")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.ADDRESS.withName("_mantissa")).withName("NSDecimal"), ptr, sel) as NSDecimal
}

// @property decimalValue
    val sel = ObjCRuntime.sel("decimalValue")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.ADDRESS.withName("_mantissa")).withName("NSDecimal"), ptr, sel) as NSDecimal
}


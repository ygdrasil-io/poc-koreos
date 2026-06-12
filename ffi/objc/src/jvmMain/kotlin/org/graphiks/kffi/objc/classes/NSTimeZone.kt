package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTimeZone
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSTimeZone(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTimeZone") }
        
    }
    
    open fun secondsFromGMTForDate(aDate: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("secondsFromGMTForDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, aDate) as NSInteger
    }
    
    open fun abbreviationForDate(aDate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("abbreviationForDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aDate) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun abbreviationForDateAsString(aDate: MemorySegment): String = ObjCRuntime.toJavaString(abbreviationForDate(aDate))
    
    open fun isDaylightSavingTimeForDate(aDate: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isDaylightSavingTimeForDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, aDate) as BOOL
    }
    
    open fun daylightSavingTimeOffsetForDate(aDate: MemorySegment): NSTimeInterval {
        val sel = ObjCRuntime.sel("daylightSavingTimeOffsetForDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, aDate) as NSTimeInterval
    }
    
    open fun nextDaylightSavingTimeTransitionAfterDate(aDate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("nextDaylightSavingTimeTransitionAfterDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aDate) as MemorySegment
    }
    
    // @property name
    open fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    // @property data
    open fun data(): MemorySegment {
        val sel = ObjCRuntime.sel("data")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSExtendedTimeZone on NSTimeZone ─────────────────────────────────────────

fun NSTimeZone.isEqualToTimeZone(aTimeZone: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEqualToTimeZone:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, aTimeZone) as BOOL
}

fun NSTimeZone.localizedName_locale(style: NSTimeZoneNameStyle, locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("localizedName:locale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, style, locale) as MemorySegment
}

fun NSTimeZone.secondsFromGMT(): NSInteger {
    val sel = ObjCRuntime.sel("secondsFromGMT")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSTimeZone.abbreviation(): MemorySegment {
    val sel = ObjCRuntime.sel("abbreviation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTimeZone.isDaylightSavingTime(): BOOL {
    val sel = ObjCRuntime.sel("isDaylightSavingTime")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTimeZone.daylightSavingTimeOffset(): NSTimeInterval {
    val sel = ObjCRuntime.sel("daylightSavingTimeOffset")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
}

fun NSTimeZone.nextDaylightSavingTimeTransition(): MemorySegment {
    val sel = ObjCRuntime.sel("nextDaylightSavingTimeTransition")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTimeZone.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class<*> method: +[NSTimeZone resetSystemTimeZone]
fun NSTimeZone_resetSystemTimeZone(): Unit {
    val sel = ObjCRuntime.sel("resetSystemTimeZone")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    ObjCRuntime.msgSend(null, cls, sel)
}

// Class<*> method: +[NSTimeZone abbreviationDictionary]
fun NSTimeZone_abbreviationDictionary(): MemorySegment {
    val sel = ObjCRuntime.sel("abbreviationDictionary")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSTimeZone systemTimeZone]
fun NSTimeZone_systemTimeZone(): MemorySegment {
    val sel = ObjCRuntime.sel("systemTimeZone")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSTimeZone defaultTimeZone]
fun NSTimeZone_defaultTimeZone(): MemorySegment {
    val sel = ObjCRuntime.sel("defaultTimeZone")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSTimeZone setDefaultTimeZone:]
fun NSTimeZone_setDefaultTimeZone(defaultTimeZone: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setDefaultTimeZone:")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    ObjCRuntime.msgSend(null, cls, sel, defaultTimeZone)
}

// Class<*> method: +[NSTimeZone localTimeZone]
fun NSTimeZone_localTimeZone(): MemorySegment {
    val sel = ObjCRuntime.sel("localTimeZone")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSTimeZone knownTimeZoneNames]
fun NSTimeZone_knownTimeZoneNames(): MemorySegment {
    val sel = ObjCRuntime.sel("knownTimeZoneNames")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSTimeZone setAbbreviationDictionary:]
fun NSTimeZone_setAbbreviationDictionary(abbreviationDictionary: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAbbreviationDictionary:")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    ObjCRuntime.msgSend(null, cls, sel, abbreviationDictionary)
}

// Class<*> method: +[NSTimeZone timeZoneDataVersion]
fun NSTimeZone_timeZoneDataVersion(): MemorySegment {
    val sel = ObjCRuntime.sel("timeZoneDataVersion")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// @property systemTimeZone
fun NSTimeZone.systemTimeZone(): MemorySegment {
    val sel = ObjCRuntime.sel("systemTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property defaultTimeZone
fun NSTimeZone.defaultTimeZone(): MemorySegment {
    val sel = ObjCRuntime.sel("defaultTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSTimeZone.setDefaultTimeZone(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setDefaultTimeZone:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property localTimeZone
fun NSTimeZone.localTimeZone(): MemorySegment {
    val sel = ObjCRuntime.sel("localTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property knownTimeZoneNames
/** @return NSArray<NSString *> * */
fun NSTimeZone.knownTimeZoneNames(): MemorySegment {
    val sel = ObjCRuntime.sel("knownTimeZoneNames")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property abbreviationDictionary
/** @return NSDictionary<NSString *,NSString *> * */
fun NSTimeZone.abbreviationDictionary(): MemorySegment {
    val sel = ObjCRuntime.sel("abbreviationDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSTimeZone.setAbbreviationDictionary(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setAbbreviationDictionary:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property timeZoneDataVersion
fun NSTimeZone.timeZoneDataVersion(): MemorySegment {
    val sel = ObjCRuntime.sel("timeZoneDataVersion")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property secondsFromGMT
fun NSTimeZone.initWithName(tzName: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithName:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tzName) as MemorySegment
}

fun NSTimeZone.initWithName_data(tzName: MemorySegment, aData: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithName:data:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tzName, aData) as MemorySegment
}

// Class<*> method: +[NSTimeZone timeZoneWithName:]
fun NSTimeZone_timeZoneWithName(tzName: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("timeZoneWithName:")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, tzName) as MemorySegment
}

// Class<*> method: +[NSTimeZone timeZoneWithName:data:]
fun NSTimeZone_timeZoneWithName_data(tzName: MemorySegment, aData: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("timeZoneWithName:data:")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, tzName, aData) as MemorySegment
}

// Class<*> method: +[NSTimeZone timeZoneForSecondsFromGMT:]
fun NSTimeZone_timeZoneForSecondsFromGMT(seconds: NSInteger): MemorySegment {
    val sel = ObjCRuntime.sel("timeZoneForSecondsFromGMT:")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, seconds) as MemorySegment
}

// Class<*> method: +[NSTimeZone timeZoneWithAbbreviation:]
fun NSTimeZone_timeZoneWithAbbreviation(abbreviation: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("timeZoneWithAbbreviation:")
    val cls = ObjCRuntime.getClass("NSTimeZone")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, abbreviation) as MemorySegment
}


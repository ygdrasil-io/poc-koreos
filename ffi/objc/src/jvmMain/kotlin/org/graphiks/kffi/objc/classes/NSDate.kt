package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSDate
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSDate(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDate") }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithTimeIntervalSinceReferenceDate(ti: NSTimeInterval): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTimeIntervalSinceReferenceDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ti) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    // @property timeIntervalSinceReferenceDate
    open fun timeIntervalSinceReferenceDate(): NSTimeInterval {
        val sel = ObjCRuntime.sel("timeIntervalSinceReferenceDate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
}

// ── Category: NSExtendedDate on NSDate ─────────────────────────────────────────

fun NSDate.timeIntervalSinceDate(anotherDate: MemorySegment): NSTimeInterval {
    val sel = ObjCRuntime.sel("timeIntervalSinceDate:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, anotherDate) as NSTimeInterval
}

fun NSDate.addTimeInterval(seconds: NSTimeInterval): MemorySegment {
    val sel = ObjCRuntime.sel("addTimeInterval:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, seconds) as MemorySegment
}

fun NSDate.dateByAddingTimeInterval(ti: NSTimeInterval): MemorySegment {
    val sel = ObjCRuntime.sel("dateByAddingTimeInterval:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ti) as MemorySegment
}

fun NSDate.earlierDate(anotherDate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("earlierDate:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anotherDate) as MemorySegment
}

fun NSDate.laterDate(anotherDate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("laterDate:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anotherDate) as MemorySegment
}

fun NSDate.compare(other: MemorySegment): NSComparisonResult {
    val sel = ObjCRuntime.sel("compare:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, other) as NSComparisonResult
}

fun NSDate.isEqualToDate(otherDate: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEqualToDate:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, otherDate) as BOOL
}

fun NSDate.descriptionWithLocale(locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionWithLocale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
}

fun NSDate.timeIntervalSinceNow(): NSTimeInterval {
    val sel = ObjCRuntime.sel("timeIntervalSinceNow")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
}

fun NSDate.timeIntervalSince1970(): NSTimeInterval {
    val sel = ObjCRuntime.sel("timeIntervalSince1970")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
}

fun NSDate.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class<*> method: +[NSDate timeIntervalSinceReferenceDate]
fun NSDate_timeIntervalSinceReferenceDate(): NSTimeInterval {
    val sel = ObjCRuntime.sel("timeIntervalSinceReferenceDate")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, cls, sel) as NSTimeInterval
}

// @property timeIntervalSinceNow
fun NSDate.timeIntervalSinceNow(): NSTimeInterval {
    val sel = ObjCRuntime.sel("timeIntervalSinceNow")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
}

// @property timeIntervalSince1970
fun NSDate.timeIntervalSince1970(): NSTimeInterval {
    val sel = ObjCRuntime.sel("timeIntervalSince1970")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
}

// @property description
fun NSDate.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property timeIntervalSinceReferenceDate
fun NSDate.timeIntervalSinceReferenceDate(): NSTimeInterval {
    val sel = ObjCRuntime.sel("timeIntervalSinceReferenceDate")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
}

// ── Category: NSDateCreation on NSDate ─────────────────────────────────────────

fun NSDate.initWithTimeIntervalSinceNow(secs: NSTimeInterval): MemorySegment {
    val sel = ObjCRuntime.sel("initWithTimeIntervalSinceNow:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, secs) as MemorySegment
}

fun NSDate.initWithTimeIntervalSince1970(secs: NSTimeInterval): MemorySegment {
    val sel = ObjCRuntime.sel("initWithTimeIntervalSince1970:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, secs) as MemorySegment
}

fun NSDate.initWithTimeInterval_sinceDate(secsToBeAdded: NSTimeInterval, date: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithTimeInterval:sinceDate:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, secsToBeAdded, date) as MemorySegment
}

// Class<*> method: +[NSDate date]
fun NSDate_date(): MemorySegment {
    val sel = ObjCRuntime.sel("date")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSDate dateWithTimeIntervalSinceNow:]
fun NSDate_dateWithTimeIntervalSinceNow(secs: NSTimeInterval): MemorySegment {
    val sel = ObjCRuntime.sel("dateWithTimeIntervalSinceNow:")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, secs) as MemorySegment
}

// Class<*> method: +[NSDate dateWithTimeIntervalSinceReferenceDate:]
fun NSDate_dateWithTimeIntervalSinceReferenceDate(ti: NSTimeInterval): MemorySegment {
    val sel = ObjCRuntime.sel("dateWithTimeIntervalSinceReferenceDate:")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, ti) as MemorySegment
}

// Class<*> method: +[NSDate dateWithTimeIntervalSince1970:]
fun NSDate_dateWithTimeIntervalSince1970(secs: NSTimeInterval): MemorySegment {
    val sel = ObjCRuntime.sel("dateWithTimeIntervalSince1970:")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, secs) as MemorySegment
}

// Class<*> method: +[NSDate dateWithTimeInterval:sinceDate:]
fun NSDate_dateWithTimeInterval_sinceDate(secsToBeAdded: NSTimeInterval, date: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dateWithTimeInterval:sinceDate:")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, secsToBeAdded, date) as MemorySegment
}

// Class<*> method: +[NSDate distantFuture]
fun NSDate_distantFuture(): MemorySegment {
    val sel = ObjCRuntime.sel("distantFuture")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSDate distantPast]
fun NSDate_distantPast(): MemorySegment {
    val sel = ObjCRuntime.sel("distantPast")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSDate now]
fun NSDate_now(): MemorySegment {
    val sel = ObjCRuntime.sel("now")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// @property distantFuture
fun NSDate.distantFuture(): MemorySegment {
    val sel = ObjCRuntime.sel("distantFuture")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property distantPast
fun NSDate.distantPast(): MemorySegment {
    val sel = ObjCRuntime.sel("distantPast")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property now
fun NSDate.now(): MemorySegment {
    val sel = ObjCRuntime.sel("now")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSCalendarDateExtras on NSDate ─────────────────────────────────────────

fun NSDate.dateWithCalendarFormat_timeZone(format: MemorySegment, aTimeZone: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dateWithCalendarFormat:timeZone:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, aTimeZone) as MemorySegment
}

fun NSDate.descriptionWithCalendarFormat_timeZone_locale(format: MemorySegment, aTimeZone: MemorySegment, locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionWithCalendarFormat:timeZone:locale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, aTimeZone, locale) as MemorySegment
}

fun NSDate.initWithString(description: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, description) as MemorySegment
}

// Class<*> method: +[NSDate dateWithNaturalLanguageString:locale:]
fun NSDate_dateWithNaturalLanguageString_locale(string: MemorySegment, locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dateWithNaturalLanguageString:locale:")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, string, locale) as MemorySegment
}

// Class<*> method: +[NSDate dateWithNaturalLanguageString:]
fun NSDate_dateWithNaturalLanguageString(string: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dateWithNaturalLanguageString:")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, string) as MemorySegment
}

// Class<*> method: +[NSDate dateWithString:]
fun NSDate_dateWithString(aString: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dateWithString:")
    val cls = ObjCRuntime.getClass("NSDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, aString) as MemorySegment
}


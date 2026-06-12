package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCalendarDate
 * Superclass: NSDate
 */
open class NSCalendarDate(ptr: MemorySegment) : NSDate(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCalendarDate") }
        
        fun calendarDate(): MemorySegment {
            val sel = ObjCRuntime.sel("calendarDate")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun dateWithString_calendarFormat_locale(description: MemorySegment, format: MemorySegment, locale: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dateWithString:calendarFormat:locale:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, description, format, locale) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun dateWithString_calendarFormat_locale(description: String, format: String, locale: MemorySegment): MemorySegment = dateWithString_calendarFormat_locale(ObjCRuntime.newNSString(Arena.global(), description), ObjCRuntime.newNSString(Arena.global(), format), locale)
        
        fun dateWithString_calendarFormat(description: MemorySegment, format: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dateWithString:calendarFormat:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, description, format) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun dateWithString_calendarFormat(description: String, format: String): MemorySegment = dateWithString_calendarFormat(ObjCRuntime.newNSString(Arena.global(), description), ObjCRuntime.newNSString(Arena.global(), format))
        
        fun dateWithYear_month_day_hour_minute_second_timeZone(year: NSInteger, month: NSUInteger, day: NSUInteger, hour: NSUInteger, minute: NSUInteger, second: NSUInteger, aTimeZone: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dateWithYear:month:day:hour:minute:second:timeZone:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, year, month, day, hour, minute, second, aTimeZone) as MemorySegment
        }
        
        override fun `distantFuture`(): MemorySegment {
            val sel = ObjCRuntime.sel("distantFuture")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        override fun `distantPast`(): MemorySegment {
            val sel = ObjCRuntime.sel("distantPast")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun dateByAddingYears_months_days_hours_minutes_seconds(year: NSInteger, month: NSInteger, day: NSInteger, hour: NSInteger, minute: NSInteger, second: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("dateByAddingYears:months:days:hours:minutes:seconds:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, year, month, day, hour, minute, second) as MemorySegment
    }
    
    fun dayOfCommonEra(): NSInteger {
        val sel = ObjCRuntime.sel("dayOfCommonEra")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun dayOfMonth(): NSInteger {
        val sel = ObjCRuntime.sel("dayOfMonth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun dayOfWeek(): NSInteger {
        val sel = ObjCRuntime.sel("dayOfWeek")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun dayOfYear(): NSInteger {
        val sel = ObjCRuntime.sel("dayOfYear")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun hourOfDay(): NSInteger {
        val sel = ObjCRuntime.sel("hourOfDay")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun minuteOfHour(): NSInteger {
        val sel = ObjCRuntime.sel("minuteOfHour")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun monthOfYear(): NSInteger {
        val sel = ObjCRuntime.sel("monthOfYear")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun secondOfMinute(): NSInteger {
        val sel = ObjCRuntime.sel("secondOfMinute")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun yearOfCommonEra(): NSInteger {
        val sel = ObjCRuntime.sel("yearOfCommonEra")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun calendarFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("calendarFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun calendarFormatAsString(): String = ObjCRuntime.toJavaString(calendarFormat())
    
    fun descriptionWithCalendarFormat_locale(format: MemorySegment, locale: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("descriptionWithCalendarFormat:locale:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, locale) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun descriptionWithCalendarFormat_localeAsString(format: MemorySegment, locale: MemorySegment): String = ObjCRuntime.toJavaString(descriptionWithCalendarFormat_locale(format, locale))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun descriptionWithCalendarFormat_locale(format: String, locale: MemorySegment): MemorySegment = descriptionWithCalendarFormat_locale(ObjCRuntime.newNSString(Arena.global(), format), locale)
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun descriptionWithCalendarFormat_localeAsString(format: String, locale: MemorySegment): String = ObjCRuntime.toJavaString(descriptionWithCalendarFormat_locale(ObjCRuntime.newNSString(Arena.global(), format), locale))
    
    fun descriptionWithCalendarFormat(format: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("descriptionWithCalendarFormat:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun descriptionWithCalendarFormatAsString(format: MemorySegment): String = ObjCRuntime.toJavaString(descriptionWithCalendarFormat(format))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun descriptionWithCalendarFormat(format: String): MemorySegment = descriptionWithCalendarFormat(ObjCRuntime.newNSString(Arena.global(), format))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun descriptionWithCalendarFormatAsString(format: String): String = ObjCRuntime.toJavaString(descriptionWithCalendarFormat(ObjCRuntime.newNSString(Arena.global(), format)))
    
    override fun `descriptionWithLocale`(locale: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("descriptionWithLocale:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun descriptionWithLocaleAsString(locale: MemorySegment): String = ObjCRuntime.toJavaString(descriptionWithLocale(locale))
    
    fun timeZone(): MemorySegment {
        val sel = ObjCRuntime.sel("timeZone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithString_calendarFormat_locale(description: MemorySegment, format: MemorySegment, locale: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:calendarFormat:locale:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, description, format, locale) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString_calendarFormat_locale(description: String, format: String, locale: MemorySegment): MemorySegment = initWithString_calendarFormat_locale(ObjCRuntime.newNSString(Arena.global(), description), ObjCRuntime.newNSString(Arena.global(), format), locale)
    
    fun initWithString_calendarFormat(description: MemorySegment, format: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:calendarFormat:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, description, format) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString_calendarFormat(description: String, format: String): MemorySegment = initWithString_calendarFormat(ObjCRuntime.newNSString(Arena.global(), description), ObjCRuntime.newNSString(Arena.global(), format))
    
    override fun `initWithString`(description: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, description) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    override fun `initWithString`(description: String): MemorySegment = initWithString(ObjCRuntime.newNSString(Arena.global(), description))
    
    fun initWithYear_month_day_hour_minute_second_timeZone(year: NSInteger, month: NSUInteger, day: NSUInteger, hour: NSUInteger, minute: NSUInteger, second: NSUInteger, aTimeZone: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithYear:month:day:hour:minute:second:timeZone:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, year, month, day, hour, minute, second, aTimeZone) as MemorySegment
    }
    
    fun setCalendarFormat(format: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setCalendarFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, format)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setCalendarFormat(format: String): Unit = setCalendarFormat(ObjCRuntime.newNSString(Arena.global(), format))
    
    fun setTimeZone(aTimeZone: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setTimeZone:")
        ObjCRuntime.msgSend(null, ptr, sel, aTimeZone)
    }
    
    fun years_months_days_hours_minutes_seconds_sinceDate(yp: MemorySegment, mop: MemorySegment, dp: MemorySegment, hp: MemorySegment, mip: MemorySegment, sp: MemorySegment, date: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("years:months:days:hours:minutes:seconds:sinceDate:")
        ObjCRuntime.msgSend(null, ptr, sel, yp, mop, dp, hp, mip, sp, date)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: refCount: NSUInteger
    // ivar: _timeIntervalSinceReferenceDate: NSTimeInterval
    // ivar: _timeZone: MemorySegment
    // ivar: _formatString: MemorySegment
    // ivar: _reserved: MemorySegment
}


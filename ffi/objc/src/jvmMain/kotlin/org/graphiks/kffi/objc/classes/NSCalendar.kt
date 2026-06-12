package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCalendar
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSCalendar(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCalendar") }
        
        open fun calendarWithIdentifier(calendarIdentifierConstant: NSCalendarIdentifier): MemorySegment {
            val sel = ObjCRuntime.sel("calendarWithIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, calendarIdentifierConstant) as MemorySegment
        }
        
        open fun currentCalendar(): MemorySegment {
            val sel = ObjCRuntime.sel("currentCalendar")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun autoupdatingCurrentCalendar(): MemorySegment {
            val sel = ObjCRuntime.sel("autoupdatingCurrentCalendar")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithCalendarIdentifier(ident: NSCalendarIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCalendarIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ident) as MemorySegment
    }
    
    open fun minimumRangeOfUnit(unit: NSCalendarUnit): NSRange {
        val sel = ObjCRuntime.sel("minimumRangeOfUnit:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, unit) as NSRange
    }
    
    open fun maximumRangeOfUnit(unit: NSCalendarUnit): NSRange {
        val sel = ObjCRuntime.sel("maximumRangeOfUnit:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, unit) as NSRange
    }
    
    open fun rangeOfUnit_inUnit_forDate(smaller: NSCalendarUnit, larger: NSCalendarUnit, date: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("rangeOfUnit:inUnit:forDate:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, smaller, larger, date) as NSRange
    }
    
    open fun ordinalityOfUnit_inUnit_forDate(smaller: NSCalendarUnit, larger: NSCalendarUnit, date: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("ordinalityOfUnit:inUnit:forDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, smaller, larger, date) as NSUInteger
    }
    
    open fun rangeOfUnit_startDate_interval_forDate(unit: NSCalendarUnit, datep: MemorySegment, tip: MemorySegment, date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("rangeOfUnit:startDate:interval:forDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, unit, datep, tip, date) as BOOL
    }
    
    open fun dateFromComponents(comps: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("dateFromComponents:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, comps) as MemorySegment
    }
    
    open fun components_fromDate(unitFlags: NSCalendarUnit, date: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("components:fromDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, unitFlags, date) as MemorySegment
    }
    
    open fun dateByAddingComponents_toDate_options(comps: MemorySegment, date: MemorySegment, opts: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("dateByAddingComponents:toDate:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, comps, date, opts) as MemorySegment
    }
    
    open fun components_fromDate_toDate_options(unitFlags: NSCalendarUnit, startingDate: MemorySegment, resultDate: MemorySegment, opts: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("components:fromDate:toDate:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, unitFlags, startingDate, resultDate, opts) as MemorySegment
    }
    
    open fun getEra_year_month_day_fromDate(eraValuePointer: MemorySegment, yearValuePointer: MemorySegment, monthValuePointer: MemorySegment, dayValuePointer: MemorySegment, date: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getEra:year:month:day:fromDate:")
        ObjCRuntime.msgSend(null, ptr, sel, eraValuePointer, yearValuePointer, monthValuePointer, dayValuePointer, date)
    }
    
    open fun getEra_yearForWeekOfYear_weekOfYear_weekday_fromDate(eraValuePointer: MemorySegment, yearValuePointer: MemorySegment, weekValuePointer: MemorySegment, weekdayValuePointer: MemorySegment, date: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getEra:yearForWeekOfYear:weekOfYear:weekday:fromDate:")
        ObjCRuntime.msgSend(null, ptr, sel, eraValuePointer, yearValuePointer, weekValuePointer, weekdayValuePointer, date)
    }
    
    open fun getHour_minute_second_nanosecond_fromDate(hourValuePointer: MemorySegment, minuteValuePointer: MemorySegment, secondValuePointer: MemorySegment, nanosecondValuePointer: MemorySegment, date: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getHour:minute:second:nanosecond:fromDate:")
        ObjCRuntime.msgSend(null, ptr, sel, hourValuePointer, minuteValuePointer, secondValuePointer, nanosecondValuePointer, date)
    }
    
    open fun component_fromDate(unit: NSCalendarUnit, date: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("component:fromDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, unit, date) as NSInteger
    }
    
    open fun dateWithEra_year_month_day_hour_minute_second_nanosecond(eraValue: NSInteger, yearValue: NSInteger, monthValue: NSInteger, dayValue: NSInteger, hourValue: NSInteger, minuteValue: NSInteger, secondValue: NSInteger, nanosecondValue: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("dateWithEra:year:month:day:hour:minute:second:nanosecond:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, eraValue, yearValue, monthValue, dayValue, hourValue, minuteValue, secondValue, nanosecondValue) as MemorySegment
    }
    
    open fun dateWithEra_yearForWeekOfYear_weekOfYear_weekday_hour_minute_second_nanosecond(eraValue: NSInteger, yearValue: NSInteger, weekValue: NSInteger, weekdayValue: NSInteger, hourValue: NSInteger, minuteValue: NSInteger, secondValue: NSInteger, nanosecondValue: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("dateWithEra:yearForWeekOfYear:weekOfYear:weekday:hour:minute:second:nanosecond:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, eraValue, yearValue, weekValue, weekdayValue, hourValue, minuteValue, secondValue, nanosecondValue) as MemorySegment
    }
    
    open fun startOfDayForDate(date: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("startOfDayForDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date) as MemorySegment
    }
    
    open fun componentsInTimeZone_fromDate(timezone: MemorySegment, date: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("componentsInTimeZone:fromDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, timezone, date) as MemorySegment
    }
    
    open fun compareDate_toDate_toUnitGranularity(date1: MemorySegment, date2: MemorySegment, unit: NSCalendarUnit): NSComparisonResult {
        val sel = ObjCRuntime.sel("compareDate:toDate:toUnitGranularity:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date1, date2, unit) as NSComparisonResult
    }
    
    open fun isDate_equalToDate_toUnitGranularity(date1: MemorySegment, date2: MemorySegment, unit: NSCalendarUnit): BOOL {
        val sel = ObjCRuntime.sel("isDate:equalToDate:toUnitGranularity:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date1, date2, unit) as BOOL
    }
    
    open fun isDate_inSameDayAsDate(date1: MemorySegment, date2: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isDate:inSameDayAsDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date1, date2) as BOOL
    }
    
    open fun isDateInToday(date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isDateInToday:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date) as BOOL
    }
    
    open fun isDateInYesterday(date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isDateInYesterday:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date) as BOOL
    }
    
    open fun isDateInTomorrow(date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isDateInTomorrow:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date) as BOOL
    }
    
    open fun isDateInWeekend(date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isDateInWeekend:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date) as BOOL
    }
    
    open fun rangeOfWeekendStartDate_interval_containingDate(datep: MemorySegment, tip: MemorySegment, date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("rangeOfWeekendStartDate:interval:containingDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, datep, tip, date) as BOOL
    }
    
    open fun nextWeekendStartDate_interval_options_afterDate(datep: MemorySegment, tip: MemorySegment, options: NSCalendarOptions, date: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("nextWeekendStartDate:interval:options:afterDate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, datep, tip, options, date) as BOOL
    }
    
    open fun components_fromDateComponents_toDateComponents_options(unitFlags: NSCalendarUnit, startingDateComp: MemorySegment, resultDateComp: MemorySegment, options: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("components:fromDateComponents:toDateComponents:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, unitFlags, startingDateComp, resultDateComp, options) as MemorySegment
    }
    
    open fun dateByAddingUnit_value_toDate_options(unit: NSCalendarUnit, value: NSInteger, date: MemorySegment, options: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("dateByAddingUnit:value:toDate:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, unit, value, date, options) as MemorySegment
    }
    
    open fun enumerateDatesStartingAfterDate_matchingComponents_options_usingBlock(start: MemorySegment, comps: MemorySegment, opts: NSCalendarOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateDatesStartingAfterDate:matchingComponents:options:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, start, comps, opts, block)
    }
    
    open fun nextDateAfterDate_matchingComponents_options(date: MemorySegment, comps: MemorySegment, options: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("nextDateAfterDate:matchingComponents:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date, comps, options) as MemorySegment
    }
    
    open fun nextDateAfterDate_matchingUnit_value_options(date: MemorySegment, unit: NSCalendarUnit, value: NSInteger, options: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("nextDateAfterDate:matchingUnit:value:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date, unit, value, options) as MemorySegment
    }
    
    open fun nextDateAfterDate_matchingHour_minute_second_options(date: MemorySegment, hourValue: NSInteger, minuteValue: NSInteger, secondValue: NSInteger, options: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("nextDateAfterDate:matchingHour:minute:second:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date, hourValue, minuteValue, secondValue, options) as MemorySegment
    }
    
    open fun dateBySettingUnit_value_ofDate_options(unit: NSCalendarUnit, v: NSInteger, date: MemorySegment, opts: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("dateBySettingUnit:value:ofDate:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, unit, v, date, opts) as MemorySegment
    }
    
    open fun dateBySettingHour_minute_second_ofDate_options(h: NSInteger, m: NSInteger, s: NSInteger, date: MemorySegment, opts: NSCalendarOptions): MemorySegment {
        val sel = ObjCRuntime.sel("dateBySettingHour:minute:second:ofDate:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, h, m, s, date, opts) as MemorySegment
    }
    
    open fun date_matchesComponents(date: MemorySegment, components: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("date:matchesComponents:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, date, components) as BOOL
    }
    
    // @property currentCalendar
    open fun currentCalendar(): MemorySegment {
        val sel = ObjCRuntime.sel("currentCalendar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property autoupdatingCurrentCalendar
    open fun autoupdatingCurrentCalendar(): MemorySegment {
        val sel = ObjCRuntime.sel("autoupdatingCurrentCalendar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property calendarIdentifier
    open fun calendarIdentifier(): NSCalendarIdentifier {
        val sel = ObjCRuntime.sel("calendarIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCalendarIdentifier
    }
    
    // @property locale
    open fun locale(): MemorySegment {
        val sel = ObjCRuntime.sel("locale")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setLocale(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocale:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeZone
    open fun timeZone(): MemorySegment {
        val sel = ObjCRuntime.sel("timeZone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTimeZone(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTimeZone:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property firstWeekday
    open fun firstWeekday(): NSUInteger {
        val sel = ObjCRuntime.sel("firstWeekday")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    open fun setFirstWeekday(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setFirstWeekday:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumDaysInFirstWeek
    open fun minimumDaysInFirstWeek(): NSUInteger {
        val sel = ObjCRuntime.sel("minimumDaysInFirstWeek")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    open fun setMinimumDaysInFirstWeek(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMinimumDaysInFirstWeek:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property eraSymbols
    /** @return NSArray<NSString *> * */
    open fun eraSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("eraSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property longEraSymbols
    /** @return NSArray<NSString *> * */
    open fun longEraSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("longEraSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property monthSymbols
    /** @return NSArray<NSString *> * */
    open fun monthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("monthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shortMonthSymbols
    /** @return NSArray<NSString *> * */
    open fun shortMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property veryShortMonthSymbols
    /** @return NSArray<NSString *> * */
    open fun veryShortMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("veryShortMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property standaloneMonthSymbols
    /** @return NSArray<NSString *> * */
    open fun standaloneMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("standaloneMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shortStandaloneMonthSymbols
    /** @return NSArray<NSString *> * */
    open fun shortStandaloneMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortStandaloneMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property veryShortStandaloneMonthSymbols
    /** @return NSArray<NSString *> * */
    open fun veryShortStandaloneMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("veryShortStandaloneMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property weekdaySymbols
    /** @return NSArray<NSString *> * */
    open fun weekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("weekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shortWeekdaySymbols
    /** @return NSArray<NSString *> * */
    open fun shortWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property veryShortWeekdaySymbols
    /** @return NSArray<NSString *> * */
    open fun veryShortWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("veryShortWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property standaloneWeekdaySymbols
    /** @return NSArray<NSString *> * */
    open fun standaloneWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("standaloneWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shortStandaloneWeekdaySymbols
    /** @return NSArray<NSString *> * */
    open fun shortStandaloneWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortStandaloneWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property veryShortStandaloneWeekdaySymbols
    /** @return NSArray<NSString *> * */
    open fun veryShortStandaloneWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("veryShortStandaloneWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property quarterSymbols
    /** @return NSArray<NSString *> * */
    open fun quarterSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("quarterSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shortQuarterSymbols
    /** @return NSArray<NSString *> * */
    open fun shortQuarterSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortQuarterSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property standaloneQuarterSymbols
    /** @return NSArray<NSString *> * */
    open fun standaloneQuarterSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("standaloneQuarterSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shortStandaloneQuarterSymbols
    /** @return NSArray<NSString *> * */
    open fun shortStandaloneQuarterSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortStandaloneQuarterSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property AMSymbol
    open fun AMSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("AMSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun AMSymbolAsString(): String = ObjCRuntime.toJavaString(AMSymbol())
    
    // @property PMSymbol
    open fun PMSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("PMSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun PMSymbolAsString(): String = ObjCRuntime.toJavaString(PMSymbol())
    
}


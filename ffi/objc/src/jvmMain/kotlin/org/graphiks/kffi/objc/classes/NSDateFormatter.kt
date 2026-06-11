/**
 * Kotlin/JVM wrapper for Objective-C class: NSDateFormatter
 * Superclass: NSFormatter
 */
open class NSDateFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDateFormatter") }
        
        fun localizedStringFromDate_dateStyle_timeStyle(date: MemorySegment, dstyle: NSDateFormatterStyle, tstyle: NSDateFormatterStyle): MemorySegment {
            val sel = ObjCRuntime.sel("localizedStringFromDate:dateStyle:timeStyle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, date, dstyle, tstyle) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun localizedStringFromDate_dateStyle_timeStyleAsString(date: MemorySegment, dstyle: NSDateFormatterStyle, tstyle: NSDateFormatterStyle): String = ObjCRuntime.toJavaString(localizedStringFromDate_dateStyle_timeStyle(date, dstyle, tstyle))
        
        fun dateFormatFromTemplate_options_locale(tmplate: MemorySegment, opts: NSUInteger, locale: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dateFormatFromTemplate:options:locale:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, tmplate, opts, locale) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun dateFormatFromTemplate_options_localeAsString(tmplate: MemorySegment, opts: NSUInteger, locale: MemorySegment): String = ObjCRuntime.toJavaString(dateFormatFromTemplate_options_locale(tmplate, opts, locale))
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun dateFormatFromTemplate_options_locale(tmplate: String, opts: NSUInteger, locale: MemorySegment): MemorySegment = dateFormatFromTemplate_options_locale(ObjCRuntime.newNSString(Arena.global(), tmplate), opts, locale)
        
        /** Convenience overload — [String] parameters and [String] return type. */
        fun dateFormatFromTemplate_options_localeAsString(tmplate: String, opts: NSUInteger, locale: MemorySegment): String = ObjCRuntime.toJavaString(dateFormatFromTemplate_options_locale(ObjCRuntime.newNSString(Arena.global(), tmplate), opts, locale))
        
        fun defaultFormatterBehavior(): NSDateFormatterBehavior {
            val sel = ObjCRuntime.sel("defaultFormatterBehavior")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSDateFormatterBehavior
        }
        
        fun setDefaultFormatterBehavior(defaultFormatterBehavior: NSDateFormatterBehavior): Unit {
            val sel = ObjCRuntime.sel("setDefaultFormatterBehavior:")
            ObjCRuntime.msgSend(null, _class, sel, defaultFormatterBehavior)
        }
        
    }
    
    fun getObjectValue_forString_range_error(obj: MemorySegment, string: MemorySegment, rangep: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getObjectValue:forString:range:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, obj, string, rangep, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun getObjectValue_forString_range_error(obj: MemorySegment, string: String, rangep: MemorySegment, error: MemorySegment): BOOL = getObjectValue_forString_range_error(obj, ObjCRuntime.newNSString(Arena.global(), string), rangep, error)
    
    fun stringFromDate(date: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromDate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, date) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromDateAsString(date: MemorySegment): String = ObjCRuntime.toJavaString(stringFromDate(date))
    
    fun dateFromString(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("dateFromString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun dateFromString(string: String): MemorySegment = dateFromString(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun setLocalizedDateFormatFromTemplate(dateFormatTemplate: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setLocalizedDateFormatFromTemplate:")
        ObjCRuntime.msgSend(null, ptr, sel, dateFormatTemplate)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setLocalizedDateFormatFromTemplate(dateFormatTemplate: String): Unit = setLocalizedDateFormatFromTemplate(ObjCRuntime.newNSString(Arena.global(), dateFormatTemplate))
    
    // @property formattingContext
    fun formattingContext(): NSFormattingContext {
        val sel = ObjCRuntime.sel("formattingContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFormattingContext
    }
    fun setFormattingContext(value: NSFormattingContext) {
        val sel = ObjCRuntime.sel("setFormattingContext:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultFormatterBehavior
    fun defaultFormatterBehavior(): NSDateFormatterBehavior {
        val sel = ObjCRuntime.sel("defaultFormatterBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDateFormatterBehavior
    }
    fun setDefaultFormatterBehavior(value: NSDateFormatterBehavior) {
        val sel = ObjCRuntime.sel("setDefaultFormatterBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property dateFormat
    fun dateFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("dateFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDateFormat(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDateFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun dateFormatAsString(): String = ObjCRuntime.toJavaString(dateFormat())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setDateFormat(value: String) = setDateFormat(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property dateStyle
    fun dateStyle(): NSDateFormatterStyle {
        val sel = ObjCRuntime.sel("dateStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDateFormatterStyle
    }
    fun setDateStyle(value: NSDateFormatterStyle) {
        val sel = ObjCRuntime.sel("setDateStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeStyle
    fun timeStyle(): NSDateFormatterStyle {
        val sel = ObjCRuntime.sel("timeStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDateFormatterStyle
    }
    fun setTimeStyle(value: NSDateFormatterStyle) {
        val sel = ObjCRuntime.sel("setTimeStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property locale
    fun locale(): MemorySegment {
        val sel = ObjCRuntime.sel("locale")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLocale(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocale:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property generatesCalendarDates
    fun generatesCalendarDates(): BOOL {
        val sel = ObjCRuntime.sel("generatesCalendarDates")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setGeneratesCalendarDates(value: BOOL) {
        val sel = ObjCRuntime.sel("setGeneratesCalendarDates:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property formatterBehavior
    fun formatterBehavior(): NSDateFormatterBehavior {
        val sel = ObjCRuntime.sel("formatterBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDateFormatterBehavior
    }
    fun setFormatterBehavior(value: NSDateFormatterBehavior) {
        val sel = ObjCRuntime.sel("setFormatterBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property timeZone
    fun timeZone(): MemorySegment {
        val sel = ObjCRuntime.sel("timeZone")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTimeZone(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTimeZone:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property calendar
    fun calendar(): MemorySegment {
        val sel = ObjCRuntime.sel("calendar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCalendar(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCalendar:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lenient
    fun isLenient(): BOOL {
        val sel = ObjCRuntime.sel("isLenient")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setLenient(value: BOOL) {
        val sel = ObjCRuntime.sel("setLenient:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property twoDigitStartDate
    fun twoDigitStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("twoDigitStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTwoDigitStartDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTwoDigitStartDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultDate
    fun defaultDate(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDefaultDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDefaultDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property eraSymbols
    /** @return NSArray<NSString *> * */
    fun eraSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("eraSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setEraSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEraSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property monthSymbols
    /** @return NSArray<NSString *> * */
    fun monthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("monthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMonthSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMonthSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shortMonthSymbols
    /** @return NSArray<NSString *> * */
    fun shortMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setShortMonthSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShortMonthSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property weekdaySymbols
    /** @return NSArray<NSString *> * */
    fun weekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("weekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setWeekdaySymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setWeekdaySymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shortWeekdaySymbols
    /** @return NSArray<NSString *> * */
    fun shortWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setShortWeekdaySymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShortWeekdaySymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property AMSymbol
    fun AMSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("AMSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAMSymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAMSymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun AMSymbolAsString(): String = ObjCRuntime.toJavaString(AMSymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setAMSymbol(value: String) = setAMSymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property PMSymbol
    fun PMSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("PMSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPMSymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPMSymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun PMSymbolAsString(): String = ObjCRuntime.toJavaString(PMSymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPMSymbol(value: String) = setPMSymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property longEraSymbols
    /** @return NSArray<NSString *> * */
    fun longEraSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("longEraSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLongEraSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLongEraSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property veryShortMonthSymbols
    /** @return NSArray<NSString *> * */
    fun veryShortMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("veryShortMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setVeryShortMonthSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setVeryShortMonthSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standaloneMonthSymbols
    /** @return NSArray<NSString *> * */
    fun standaloneMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("standaloneMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandaloneMonthSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandaloneMonthSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shortStandaloneMonthSymbols
    /** @return NSArray<NSString *> * */
    fun shortStandaloneMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortStandaloneMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setShortStandaloneMonthSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShortStandaloneMonthSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property veryShortStandaloneMonthSymbols
    /** @return NSArray<NSString *> * */
    fun veryShortStandaloneMonthSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("veryShortStandaloneMonthSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setVeryShortStandaloneMonthSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setVeryShortStandaloneMonthSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property veryShortWeekdaySymbols
    /** @return NSArray<NSString *> * */
    fun veryShortWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("veryShortWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setVeryShortWeekdaySymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setVeryShortWeekdaySymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standaloneWeekdaySymbols
    /** @return NSArray<NSString *> * */
    fun standaloneWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("standaloneWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandaloneWeekdaySymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandaloneWeekdaySymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shortStandaloneWeekdaySymbols
    /** @return NSArray<NSString *> * */
    fun shortStandaloneWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortStandaloneWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setShortStandaloneWeekdaySymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShortStandaloneWeekdaySymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property veryShortStandaloneWeekdaySymbols
    /** @return NSArray<NSString *> * */
    fun veryShortStandaloneWeekdaySymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("veryShortStandaloneWeekdaySymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setVeryShortStandaloneWeekdaySymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setVeryShortStandaloneWeekdaySymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property quarterSymbols
    /** @return NSArray<NSString *> * */
    fun quarterSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("quarterSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setQuarterSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setQuarterSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shortQuarterSymbols
    /** @return NSArray<NSString *> * */
    fun shortQuarterSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortQuarterSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setShortQuarterSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShortQuarterSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standaloneQuarterSymbols
    /** @return NSArray<NSString *> * */
    fun standaloneQuarterSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("standaloneQuarterSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandaloneQuarterSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandaloneQuarterSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shortStandaloneQuarterSymbols
    /** @return NSArray<NSString *> * */
    fun shortStandaloneQuarterSymbols(): MemorySegment {
        val sel = ObjCRuntime.sel("shortStandaloneQuarterSymbols")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setShortStandaloneQuarterSymbols(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setShortStandaloneQuarterSymbols:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property gregorianStartDate
    fun gregorianStartDate(): MemorySegment {
        val sel = ObjCRuntime.sel("gregorianStartDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setGregorianStartDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setGregorianStartDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property doesRelativeDateFormatting
    fun doesRelativeDateFormatting(): BOOL {
        val sel = ObjCRuntime.sel("doesRelativeDateFormatting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDoesRelativeDateFormatting(value: BOOL) {
        val sel = ObjCRuntime.sel("setDoesRelativeDateFormatting:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _attributes: MemorySegment
    // ivar: _formatter: MemorySegment
    // ivar: _counter: NSUInteger
}

// ── Category: NSDateFormatterCompatibility on NSDateFormatter ─────────────────────────────────────────

fun NSDateFormatter.initWithDateFormat_allowNaturalLanguage(format: MemorySegment, flag: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("initWithDateFormat:allowNaturalLanguage:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format, flag) as MemorySegment
}

fun NSDateFormatter.allowsNaturalLanguage(): BOOL {
    val sel = ObjCRuntime.sel("allowsNaturalLanguage")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}


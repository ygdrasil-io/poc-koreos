package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSNumberFormatter
 * Superclass: NSFormatter
 */
open class NSNumberFormatter(ptr: MemorySegment) : NSFormatter(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNumberFormatter") }
        
        fun localizedStringFromNumber_numberStyle(num: MemorySegment, nstyle: NSNumberFormatterStyle): MemorySegment {
            val sel = ObjCRuntime.sel("localizedStringFromNumber:numberStyle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, num, nstyle) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun localizedStringFromNumber_numberStyleAsString(num: MemorySegment, nstyle: NSNumberFormatterStyle): String = ObjCRuntime.toJavaString(localizedStringFromNumber_numberStyle(num, nstyle))
        
        fun defaultFormatterBehavior(): NSNumberFormatterBehavior {
            val sel = ObjCRuntime.sel("defaultFormatterBehavior")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSNumberFormatterBehavior
        }
        
        fun setDefaultFormatterBehavior(behavior: NSNumberFormatterBehavior): Unit {
            val sel = ObjCRuntime.sel("setDefaultFormatterBehavior:")
            ObjCRuntime.msgSend(null, _class, sel, behavior)
        }
        
    }
    
    fun getObjectValue_forString_range_error(obj: MemorySegment, string: MemorySegment, rangep: MemorySegment, error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("getObjectValue:forString:range:error:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, obj, string, rangep, error) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun getObjectValue_forString_range_error(obj: MemorySegment, string: String, rangep: MemorySegment, error: MemorySegment): BOOL = getObjectValue_forString_range_error(obj, ObjCRuntime.newNSString(Arena.global(), string), rangep, error)
    
    fun stringFromNumber(number: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringFromNumber:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, number) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringFromNumberAsString(number: MemorySegment): String = ObjCRuntime.toJavaString(stringFromNumber(number))
    
    fun numberFromString(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("numberFromString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun numberFromString(string: String): MemorySegment = numberFromString(ObjCRuntime.newNSString(Arena.global(), string))
    
    // @property formattingContext
    fun formattingContext(): NSFormattingContext {
        val sel = ObjCRuntime.sel("formattingContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFormattingContext
    }
    fun setFormattingContext(value: NSFormattingContext) {
        val sel = ObjCRuntime.sel("setFormattingContext:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumGroupingDigits
    fun minimumGroupingDigits(): NSInteger {
        val sel = ObjCRuntime.sel("minimumGroupingDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMinimumGroupingDigits(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMinimumGroupingDigits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property numberStyle
    fun numberStyle(): NSNumberFormatterStyle {
        val sel = ObjCRuntime.sel("numberStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSNumberFormatterStyle
    }
    fun setNumberStyle(value: NSNumberFormatterStyle) {
        val sel = ObjCRuntime.sel("setNumberStyle:")
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
    
    // @property generatesDecimalNumbers
    fun generatesDecimalNumbers(): BOOL {
        val sel = ObjCRuntime.sel("generatesDecimalNumbers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setGeneratesDecimalNumbers(value: BOOL) {
        val sel = ObjCRuntime.sel("setGeneratesDecimalNumbers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property formatterBehavior
    fun formatterBehavior(): NSNumberFormatterBehavior {
        val sel = ObjCRuntime.sel("formatterBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSNumberFormatterBehavior
    }
    fun setFormatterBehavior(value: NSNumberFormatterBehavior) {
        val sel = ObjCRuntime.sel("setFormatterBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property negativeFormat
    fun negativeFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("negativeFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNegativeFormat(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNegativeFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun negativeFormatAsString(): String = ObjCRuntime.toJavaString(negativeFormat())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNegativeFormat(value: String) = setNegativeFormat(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property textAttributesForNegativeValues
    /** @return NSDictionary<NSString *,id> * */
    fun textAttributesForNegativeValues(): MemorySegment {
        val sel = ObjCRuntime.sel("textAttributesForNegativeValues")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextAttributesForNegativeValues(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextAttributesForNegativeValues:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property positiveFormat
    fun positiveFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("positiveFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPositiveFormat(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPositiveFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun positiveFormatAsString(): String = ObjCRuntime.toJavaString(positiveFormat())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPositiveFormat(value: String) = setPositiveFormat(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property textAttributesForPositiveValues
    /** @return NSDictionary<NSString *,id> * */
    fun textAttributesForPositiveValues(): MemorySegment {
        val sel = ObjCRuntime.sel("textAttributesForPositiveValues")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextAttributesForPositiveValues(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextAttributesForPositiveValues:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsFloats
    fun allowsFloats(): BOOL {
        val sel = ObjCRuntime.sel("allowsFloats")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsFloats(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsFloats:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property decimalSeparator
    fun decimalSeparator(): MemorySegment {
        val sel = ObjCRuntime.sel("decimalSeparator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDecimalSeparator(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDecimalSeparator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun decimalSeparatorAsString(): String = ObjCRuntime.toJavaString(decimalSeparator())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setDecimalSeparator(value: String) = setDecimalSeparator(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property alwaysShowsDecimalSeparator
    fun alwaysShowsDecimalSeparator(): BOOL {
        val sel = ObjCRuntime.sel("alwaysShowsDecimalSeparator")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAlwaysShowsDecimalSeparator(value: BOOL) {
        val sel = ObjCRuntime.sel("setAlwaysShowsDecimalSeparator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currencyDecimalSeparator
    fun currencyDecimalSeparator(): MemorySegment {
        val sel = ObjCRuntime.sel("currencyDecimalSeparator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCurrencyDecimalSeparator(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCurrencyDecimalSeparator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun currencyDecimalSeparatorAsString(): String = ObjCRuntime.toJavaString(currencyDecimalSeparator())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCurrencyDecimalSeparator(value: String) = setCurrencyDecimalSeparator(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property usesGroupingSeparator
    fun usesGroupingSeparator(): BOOL {
        val sel = ObjCRuntime.sel("usesGroupingSeparator")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesGroupingSeparator(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesGroupingSeparator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property groupingSeparator
    fun groupingSeparator(): MemorySegment {
        val sel = ObjCRuntime.sel("groupingSeparator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setGroupingSeparator(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setGroupingSeparator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun groupingSeparatorAsString(): String = ObjCRuntime.toJavaString(groupingSeparator())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setGroupingSeparator(value: String) = setGroupingSeparator(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property zeroSymbol
    fun zeroSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("zeroSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setZeroSymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setZeroSymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun zeroSymbolAsString(): String = ObjCRuntime.toJavaString(zeroSymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setZeroSymbol(value: String) = setZeroSymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property textAttributesForZero
    /** @return NSDictionary<NSString *,id> * */
    fun textAttributesForZero(): MemorySegment {
        val sel = ObjCRuntime.sel("textAttributesForZero")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextAttributesForZero(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextAttributesForZero:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property nilSymbol
    fun nilSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("nilSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNilSymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNilSymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun nilSymbolAsString(): String = ObjCRuntime.toJavaString(nilSymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNilSymbol(value: String) = setNilSymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property textAttributesForNil
    /** @return NSDictionary<NSString *,id> * */
    fun textAttributesForNil(): MemorySegment {
        val sel = ObjCRuntime.sel("textAttributesForNil")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextAttributesForNil(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextAttributesForNil:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property notANumberSymbol
    fun notANumberSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("notANumberSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNotANumberSymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNotANumberSymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun notANumberSymbolAsString(): String = ObjCRuntime.toJavaString(notANumberSymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNotANumberSymbol(value: String) = setNotANumberSymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property textAttributesForNotANumber
    /** @return NSDictionary<NSString *,id> * */
    fun textAttributesForNotANumber(): MemorySegment {
        val sel = ObjCRuntime.sel("textAttributesForNotANumber")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextAttributesForNotANumber(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextAttributesForNotANumber:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property positiveInfinitySymbol
    fun positiveInfinitySymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("positiveInfinitySymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPositiveInfinitySymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPositiveInfinitySymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun positiveInfinitySymbolAsString(): String = ObjCRuntime.toJavaString(positiveInfinitySymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPositiveInfinitySymbol(value: String) = setPositiveInfinitySymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property textAttributesForPositiveInfinity
    /** @return NSDictionary<NSString *,id> * */
    fun textAttributesForPositiveInfinity(): MemorySegment {
        val sel = ObjCRuntime.sel("textAttributesForPositiveInfinity")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextAttributesForPositiveInfinity(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextAttributesForPositiveInfinity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property negativeInfinitySymbol
    fun negativeInfinitySymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("negativeInfinitySymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNegativeInfinitySymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNegativeInfinitySymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun negativeInfinitySymbolAsString(): String = ObjCRuntime.toJavaString(negativeInfinitySymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNegativeInfinitySymbol(value: String) = setNegativeInfinitySymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property textAttributesForNegativeInfinity
    /** @return NSDictionary<NSString *,id> * */
    fun textAttributesForNegativeInfinity(): MemorySegment {
        val sel = ObjCRuntime.sel("textAttributesForNegativeInfinity")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextAttributesForNegativeInfinity(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextAttributesForNegativeInfinity:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property positivePrefix
    fun positivePrefix(): MemorySegment {
        val sel = ObjCRuntime.sel("positivePrefix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPositivePrefix(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPositivePrefix:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun positivePrefixAsString(): String = ObjCRuntime.toJavaString(positivePrefix())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPositivePrefix(value: String) = setPositivePrefix(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property positiveSuffix
    fun positiveSuffix(): MemorySegment {
        val sel = ObjCRuntime.sel("positiveSuffix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPositiveSuffix(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPositiveSuffix:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun positiveSuffixAsString(): String = ObjCRuntime.toJavaString(positiveSuffix())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPositiveSuffix(value: String) = setPositiveSuffix(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property negativePrefix
    fun negativePrefix(): MemorySegment {
        val sel = ObjCRuntime.sel("negativePrefix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNegativePrefix(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNegativePrefix:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun negativePrefixAsString(): String = ObjCRuntime.toJavaString(negativePrefix())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNegativePrefix(value: String) = setNegativePrefix(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property negativeSuffix
    fun negativeSuffix(): MemorySegment {
        val sel = ObjCRuntime.sel("negativeSuffix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setNegativeSuffix(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setNegativeSuffix:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun negativeSuffixAsString(): String = ObjCRuntime.toJavaString(negativeSuffix())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setNegativeSuffix(value: String) = setNegativeSuffix(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property currencyCode
    fun currencyCode(): MemorySegment {
        val sel = ObjCRuntime.sel("currencyCode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCurrencyCode(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCurrencyCode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun currencyCodeAsString(): String = ObjCRuntime.toJavaString(currencyCode())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCurrencyCode(value: String) = setCurrencyCode(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property currencySymbol
    fun currencySymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("currencySymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCurrencySymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCurrencySymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun currencySymbolAsString(): String = ObjCRuntime.toJavaString(currencySymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCurrencySymbol(value: String) = setCurrencySymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property internationalCurrencySymbol
    fun internationalCurrencySymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("internationalCurrencySymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setInternationalCurrencySymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setInternationalCurrencySymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun internationalCurrencySymbolAsString(): String = ObjCRuntime.toJavaString(internationalCurrencySymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setInternationalCurrencySymbol(value: String) = setInternationalCurrencySymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property percentSymbol
    fun percentSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("percentSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPercentSymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPercentSymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun percentSymbolAsString(): String = ObjCRuntime.toJavaString(percentSymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPercentSymbol(value: String) = setPercentSymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property perMillSymbol
    fun perMillSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("perMillSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPerMillSymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPerMillSymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun perMillSymbolAsString(): String = ObjCRuntime.toJavaString(perMillSymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPerMillSymbol(value: String) = setPerMillSymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property minusSign
    fun minusSign(): MemorySegment {
        val sel = ObjCRuntime.sel("minusSign")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMinusSign(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMinusSign:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun minusSignAsString(): String = ObjCRuntime.toJavaString(minusSign())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setMinusSign(value: String) = setMinusSign(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property plusSign
    fun plusSign(): MemorySegment {
        val sel = ObjCRuntime.sel("plusSign")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPlusSign(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPlusSign:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun plusSignAsString(): String = ObjCRuntime.toJavaString(plusSign())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPlusSign(value: String) = setPlusSign(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property exponentSymbol
    fun exponentSymbol(): MemorySegment {
        val sel = ObjCRuntime.sel("exponentSymbol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setExponentSymbol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setExponentSymbol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun exponentSymbolAsString(): String = ObjCRuntime.toJavaString(exponentSymbol())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setExponentSymbol(value: String) = setExponentSymbol(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property groupingSize
    fun groupingSize(): NSUInteger {
        val sel = ObjCRuntime.sel("groupingSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setGroupingSize(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setGroupingSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property secondaryGroupingSize
    fun secondaryGroupingSize(): NSUInteger {
        val sel = ObjCRuntime.sel("secondaryGroupingSize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setSecondaryGroupingSize(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setSecondaryGroupingSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property multiplier
    fun multiplier(): MemorySegment {
        val sel = ObjCRuntime.sel("multiplier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMultiplier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMultiplier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property formatWidth
    fun formatWidth(): NSUInteger {
        val sel = ObjCRuntime.sel("formatWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setFormatWidth(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setFormatWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property paddingCharacter
    fun paddingCharacter(): MemorySegment {
        val sel = ObjCRuntime.sel("paddingCharacter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPaddingCharacter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPaddingCharacter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun paddingCharacterAsString(): String = ObjCRuntime.toJavaString(paddingCharacter())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPaddingCharacter(value: String) = setPaddingCharacter(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property paddingPosition
    fun paddingPosition(): NSNumberFormatterPadPosition {
        val sel = ObjCRuntime.sel("paddingPosition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSNumberFormatterPadPosition
    }
    fun setPaddingPosition(value: NSNumberFormatterPadPosition) {
        val sel = ObjCRuntime.sel("setPaddingPosition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property roundingMode
    fun roundingMode(): NSNumberFormatterRoundingMode {
        val sel = ObjCRuntime.sel("roundingMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSNumberFormatterRoundingMode
    }
    fun setRoundingMode(value: NSNumberFormatterRoundingMode) {
        val sel = ObjCRuntime.sel("setRoundingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property roundingIncrement
    fun roundingIncrement(): MemorySegment {
        val sel = ObjCRuntime.sel("roundingIncrement")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRoundingIncrement(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRoundingIncrement:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumIntegerDigits
    fun minimumIntegerDigits(): NSUInteger {
        val sel = ObjCRuntime.sel("minimumIntegerDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setMinimumIntegerDigits(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMinimumIntegerDigits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumIntegerDigits
    fun maximumIntegerDigits(): NSUInteger {
        val sel = ObjCRuntime.sel("maximumIntegerDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setMaximumIntegerDigits(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMaximumIntegerDigits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumFractionDigits
    fun minimumFractionDigits(): NSUInteger {
        val sel = ObjCRuntime.sel("minimumFractionDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setMinimumFractionDigits(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMinimumFractionDigits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumFractionDigits
    fun maximumFractionDigits(): NSUInteger {
        val sel = ObjCRuntime.sel("maximumFractionDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setMaximumFractionDigits(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMaximumFractionDigits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimum
    fun minimum(): MemorySegment {
        val sel = ObjCRuntime.sel("minimum")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMinimum(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMinimum:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximum
    fun maximum(): MemorySegment {
        val sel = ObjCRuntime.sel("maximum")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMaximum(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMaximum:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currencyGroupingSeparator
    fun currencyGroupingSeparator(): MemorySegment {
        val sel = ObjCRuntime.sel("currencyGroupingSeparator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCurrencyGroupingSeparator(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCurrencyGroupingSeparator:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun currencyGroupingSeparatorAsString(): String = ObjCRuntime.toJavaString(currencyGroupingSeparator())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCurrencyGroupingSeparator(value: String) = setCurrencyGroupingSeparator(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property lenient
    fun isLenient(): BOOL {
        val sel = ObjCRuntime.sel("isLenient")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setLenient(value: BOOL) {
        val sel = ObjCRuntime.sel("setLenient:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesSignificantDigits
    fun usesSignificantDigits(): BOOL {
        val sel = ObjCRuntime.sel("usesSignificantDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesSignificantDigits(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesSignificantDigits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumSignificantDigits
    fun minimumSignificantDigits(): NSUInteger {
        val sel = ObjCRuntime.sel("minimumSignificantDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setMinimumSignificantDigits(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMinimumSignificantDigits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumSignificantDigits
    fun maximumSignificantDigits(): NSUInteger {
        val sel = ObjCRuntime.sel("maximumSignificantDigits")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setMaximumSignificantDigits(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMaximumSignificantDigits:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property partialStringValidationEnabled
    fun isPartialStringValidationEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isPartialStringValidationEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPartialStringValidationEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setPartialStringValidationEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSNumberFormatterCompatibility on NSNumberFormatter ─────────────────────────────────────────

fun NSNumberFormatter.hasThousandSeparators(): BOOL {
    val sel = ObjCRuntime.sel("hasThousandSeparators")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSNumberFormatter.setHasThousandSeparators(hasThousandSeparators: BOOL): Unit {
    val sel = ObjCRuntime.sel("setHasThousandSeparators:")
    ObjCRuntime.msgSend(null, ptr, sel, hasThousandSeparators)
}

fun NSNumberFormatter.thousandSeparator(): MemorySegment {
    val sel = ObjCRuntime.sel("thousandSeparator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSNumberFormatter.setThousandSeparator(thousandSeparator: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setThousandSeparator:")
    ObjCRuntime.msgSend(null, ptr, sel, thousandSeparator)
}

fun NSNumberFormatter.localizesFormat(): BOOL {
    val sel = ObjCRuntime.sel("localizesFormat")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSNumberFormatter.setLocalizesFormat(localizesFormat: BOOL): Unit {
    val sel = ObjCRuntime.sel("setLocalizesFormat:")
    ObjCRuntime.msgSend(null, ptr, sel, localizesFormat)
}

fun NSNumberFormatter.format(): MemorySegment {
    val sel = ObjCRuntime.sel("format")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSNumberFormatter.setFormat(format: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setFormat:")
    ObjCRuntime.msgSend(null, ptr, sel, format)
}

fun NSNumberFormatter.attributedStringForZero(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedStringForZero")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSNumberFormatter.setAttributedStringForZero(attributedStringForZero: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributedStringForZero:")
    ObjCRuntime.msgSend(null, ptr, sel, attributedStringForZero)
}

fun NSNumberFormatter.attributedStringForNil(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedStringForNil")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSNumberFormatter.setAttributedStringForNil(attributedStringForNil: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributedStringForNil:")
    ObjCRuntime.msgSend(null, ptr, sel, attributedStringForNil)
}

fun NSNumberFormatter.attributedStringForNotANumber(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedStringForNotANumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSNumberFormatter.setAttributedStringForNotANumber(attributedStringForNotANumber: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributedStringForNotANumber:")
    ObjCRuntime.msgSend(null, ptr, sel, attributedStringForNotANumber)
}

fun NSNumberFormatter.roundingBehavior(): MemorySegment {
    val sel = ObjCRuntime.sel("roundingBehavior")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSNumberFormatter.setRoundingBehavior(roundingBehavior: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setRoundingBehavior:")
    ObjCRuntime.msgSend(null, ptr, sel, roundingBehavior)
}

// @property hasThousandSeparators
    val sel = ObjCRuntime.sel("hasThousandSeparators")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setHasThousandSeparators:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property thousandSeparator
    val sel = ObjCRuntime.sel("thousandSeparator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setThousandSeparator:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property localizesFormat
    val sel = ObjCRuntime.sel("localizesFormat")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setLocalizesFormat:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property format
    val sel = ObjCRuntime.sel("format")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setFormat:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property attributedStringForZero
    val sel = ObjCRuntime.sel("attributedStringForZero")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setAttributedStringForZero:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property attributedStringForNil
    val sel = ObjCRuntime.sel("attributedStringForNil")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setAttributedStringForNil:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property attributedStringForNotANumber
    val sel = ObjCRuntime.sel("attributedStringForNotANumber")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setAttributedStringForNotANumber:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property roundingBehavior
    val sel = ObjCRuntime.sel("roundingBehavior")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setRoundingBehavior:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}


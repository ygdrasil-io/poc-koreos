package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserDefaults
 * Superclass: NSObject
 */
open class NSUserDefaults(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserDefaults") }
        
        open fun resetStandardUserDefaults(): Unit {
            val sel = ObjCRuntime.sel("resetStandardUserDefaults")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        open fun standardUserDefaults(): MemorySegment {
            val sel = ObjCRuntime.sel("standardUserDefaults")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithSuiteName(suitename: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSuiteName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, suitename) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithSuiteName(suitename: String): MemorySegment = initWithSuiteName(ObjCRuntime.newNSString(Arena.global(), suitename))
    
    open fun initWithUser(username: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithUser:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, username) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithUser(username: String): MemorySegment = initWithUser(ObjCRuntime.newNSString(Arena.global(), username))
    
    open fun objectForKey(defaultName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, defaultName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun objectForKey(defaultName: String): MemorySegment = objectForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun setObject_forKey(value: MemorySegment, defaultName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setObject:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, defaultName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setObject_forKey(value: MemorySegment, defaultName: String): Unit = setObject_forKey(value, ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun removeObjectForKey(defaultName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObjectForKey:")
        ObjCRuntime.msgSend(null, ptr, sel, defaultName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun removeObjectForKey(defaultName: String): Unit = removeObjectForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun stringForKey(defaultName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, defaultName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun stringForKeyAsString(defaultName: MemorySegment): String = ObjCRuntime.toJavaString(stringForKey(defaultName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun stringForKey(defaultName: String): MemorySegment = stringForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    open fun stringForKeyAsString(defaultName: String): String = ObjCRuntime.toJavaString(stringForKey(ObjCRuntime.newNSString(Arena.global(), defaultName)))
    
    open fun arrayForKey(defaultName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("arrayForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, defaultName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun arrayForKey(defaultName: String): MemorySegment = arrayForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    /** @return NSDictionary<NSString *,id> * */
    open fun dictionaryForKey(defaultName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("dictionaryForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, defaultName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun dictionaryForKey(defaultName: String): MemorySegment = dictionaryForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun dataForKey(defaultName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("dataForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, defaultName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun dataForKey(defaultName: String): MemorySegment = dataForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    /** @return NSArray<NSString *> * */
    open fun stringArrayForKey(defaultName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("stringArrayForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, defaultName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun stringArrayForKey(defaultName: String): MemorySegment = stringArrayForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun integerForKey(defaultName: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("integerForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, defaultName) as NSInteger
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun integerForKey(defaultName: String): NSInteger = integerForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun floatForKey(defaultName: MemorySegment): Float {
        val sel = ObjCRuntime.sel("floatForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, defaultName) as Float
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun floatForKey(defaultName: String): Float = floatForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun doubleForKey(defaultName: MemorySegment): Double {
        val sel = ObjCRuntime.sel("doubleForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, defaultName) as Double
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun doubleForKey(defaultName: String): Double = doubleForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun boolForKey(defaultName: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("boolForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, defaultName) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun boolForKey(defaultName: String): BOOL = boolForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun URLForKey(defaultName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, defaultName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun URLForKey(defaultName: String): MemorySegment = URLForKey(ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun setInteger_forKey(value: NSInteger, defaultName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setInteger:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, defaultName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setInteger_forKey(value: NSInteger, defaultName: String): Unit = setInteger_forKey(value, ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun setFloat_forKey(value: Float, defaultName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setFloat:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, defaultName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setFloat_forKey(value: Float, defaultName: String): Unit = setFloat_forKey(value, ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun setDouble_forKey(value: Double, defaultName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setDouble:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, defaultName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setDouble_forKey(value: Double, defaultName: String): Unit = setDouble_forKey(value, ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun setBool_forKey(value: BOOL, defaultName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setBool:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, defaultName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setBool_forKey(value: BOOL, defaultName: String): Unit = setBool_forKey(value, ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun setURL_forKey(url: MemorySegment, defaultName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setURL:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, url, defaultName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setURL_forKey(url: MemorySegment, defaultName: String): Unit = setURL_forKey(url, ObjCRuntime.newNSString(Arena.global(), defaultName))
    
    open fun registerDefaults(registrationDictionary: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerDefaults:")
        ObjCRuntime.msgSend(null, ptr, sel, registrationDictionary)
    }
    
    open fun addSuiteNamed(suiteName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addSuiteNamed:")
        ObjCRuntime.msgSend(null, ptr, sel, suiteName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun addSuiteNamed(suiteName: String): Unit = addSuiteNamed(ObjCRuntime.newNSString(Arena.global(), suiteName))
    
    open fun removeSuiteNamed(suiteName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeSuiteNamed:")
        ObjCRuntime.msgSend(null, ptr, sel, suiteName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun removeSuiteNamed(suiteName: String): Unit = removeSuiteNamed(ObjCRuntime.newNSString(Arena.global(), suiteName))
    
    /** @return NSDictionary<NSString *,id> * */
    open fun dictionaryRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("dictionaryRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** @return NSDictionary<NSString *,id> * */
    open fun volatileDomainForName(domainName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("volatileDomainForName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, domainName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun volatileDomainForName(domainName: String): MemorySegment = volatileDomainForName(ObjCRuntime.newNSString(Arena.global(), domainName))
    
    open fun setVolatileDomain_forName(domain: MemorySegment, domainName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setVolatileDomain:forName:")
        ObjCRuntime.msgSend(null, ptr, sel, domain, domainName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setVolatileDomain_forName(domain: MemorySegment, domainName: String): Unit = setVolatileDomain_forName(domain, ObjCRuntime.newNSString(Arena.global(), domainName))
    
    open fun removeVolatileDomainForName(domainName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeVolatileDomainForName:")
        ObjCRuntime.msgSend(null, ptr, sel, domainName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun removeVolatileDomainForName(domainName: String): Unit = removeVolatileDomainForName(ObjCRuntime.newNSString(Arena.global(), domainName))
    
    open fun persistentDomainNames(): MemorySegment {
        val sel = ObjCRuntime.sel("persistentDomainNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** @return NSDictionary<NSString *,id> * */
    open fun persistentDomainForName(domainName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("persistentDomainForName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, domainName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun persistentDomainForName(domainName: String): MemorySegment = persistentDomainForName(ObjCRuntime.newNSString(Arena.global(), domainName))
    
    open fun setPersistentDomain_forName(domain: MemorySegment, domainName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setPersistentDomain:forName:")
        ObjCRuntime.msgSend(null, ptr, sel, domain, domainName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setPersistentDomain_forName(domain: MemorySegment, domainName: String): Unit = setPersistentDomain_forName(domain, ObjCRuntime.newNSString(Arena.global(), domainName))
    
    open fun removePersistentDomainForName(domainName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removePersistentDomainForName:")
        ObjCRuntime.msgSend(null, ptr, sel, domainName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun removePersistentDomainForName(domainName: String): Unit = removePersistentDomainForName(ObjCRuntime.newNSString(Arena.global(), domainName))
    
    open fun synchronize(): BOOL {
        val sel = ObjCRuntime.sel("synchronize")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun objectIsForcedForKey(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("objectIsForcedForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun objectIsForcedForKey(key: String): BOOL = objectIsForcedForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    open fun objectIsForcedForKey_inDomain(key: MemorySegment, domain: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("objectIsForcedForKey:inDomain:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key, domain) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun objectIsForcedForKey_inDomain(key: String, domain: String): BOOL = objectIsForcedForKey_inDomain(ObjCRuntime.newNSString(Arena.global(), key), ObjCRuntime.newNSString(Arena.global(), domain))
    
    // @property standardUserDefaults
    open fun standardUserDefaults(): MemorySegment {
        val sel = ObjCRuntime.sel("standardUserDefaults")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property volatileDomainNames
    /** @return NSArray<NSString *> * */
    open fun volatileDomainNames(): MemorySegment {
        val sel = ObjCRuntime.sel("volatileDomainNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _kvo_: MemorySegment
    // ivar: _identifier_: MemorySegment
    // ivar: _container_: MemorySegment
}


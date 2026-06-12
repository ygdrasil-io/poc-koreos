package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScriptSuiteRegistry
 * Superclass: NSObject
 */
open class NSScriptSuiteRegistry(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScriptSuiteRegistry") }
        
        open fun sharedScriptSuiteRegistry(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedScriptSuiteRegistry")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun setSharedScriptSuiteRegistry(registry: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setSharedScriptSuiteRegistry:")
            ObjCRuntime.msgSend(null, _class, sel, registry)
        }
        
    }
    
    open fun loadSuitesFromBundle(bundle: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("loadSuitesFromBundle:")
        ObjCRuntime.msgSend(null, ptr, sel, bundle)
    }
    
    open fun loadSuiteWithDictionary_fromBundle(suiteDeclaration: MemorySegment, bundle: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("loadSuiteWithDictionary:fromBundle:")
        ObjCRuntime.msgSend(null, ptr, sel, suiteDeclaration, bundle)
    }
    
    open fun registerClassDescription(classDescription: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerClassDescription:")
        ObjCRuntime.msgSend(null, ptr, sel, classDescription)
    }
    
    open fun registerCommandDescription(commandDescription: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerCommandDescription:")
        ObjCRuntime.msgSend(null, ptr, sel, commandDescription)
    }
    
    open fun appleEventCodeForSuite(suiteName: MemorySegment): FourCharCode {
        val sel = ObjCRuntime.sel("appleEventCodeForSuite:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, suiteName) as FourCharCode
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun appleEventCodeForSuite(suiteName: String): FourCharCode = appleEventCodeForSuite(ObjCRuntime.newNSString(Arena.global(), suiteName))
    
    open fun bundleForSuite(suiteName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("bundleForSuite:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, suiteName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun bundleForSuite(suiteName: String): MemorySegment = bundleForSuite(ObjCRuntime.newNSString(Arena.global(), suiteName))
    
    /** @return NSDictionary<NSString *,NSScriptClassDescription *> * */
    open fun classDescriptionsInSuite(suiteName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("classDescriptionsInSuite:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, suiteName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun classDescriptionsInSuite(suiteName: String): MemorySegment = classDescriptionsInSuite(ObjCRuntime.newNSString(Arena.global(), suiteName))
    
    /** @return NSDictionary<NSString *,NSScriptCommandDescription *> * */
    open fun commandDescriptionsInSuite(suiteName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("commandDescriptionsInSuite:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, suiteName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun commandDescriptionsInSuite(suiteName: String): MemorySegment = commandDescriptionsInSuite(ObjCRuntime.newNSString(Arena.global(), suiteName))
    
    open fun suiteForAppleEventCode(appleEventCode: FourCharCode): MemorySegment {
        val sel = ObjCRuntime.sel("suiteForAppleEventCode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, appleEventCode) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun suiteForAppleEventCodeAsString(appleEventCode: FourCharCode): String = ObjCRuntime.toJavaString(suiteForAppleEventCode(appleEventCode))
    
    open fun classDescriptionWithAppleEventCode(appleEventCode: FourCharCode): MemorySegment {
        val sel = ObjCRuntime.sel("classDescriptionWithAppleEventCode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, appleEventCode) as MemorySegment
    }
    
    open fun commandDescriptionWithAppleEventClass_andAppleEventCode(appleEventClassCode: FourCharCode, appleEventIDCode: FourCharCode): MemorySegment {
        val sel = ObjCRuntime.sel("commandDescriptionWithAppleEventClass:andAppleEventCode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, appleEventClassCode, appleEventIDCode) as MemorySegment
    }
    
    open fun aeteResource(languageName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("aeteResource:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, languageName) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun aeteResource(languageName: String): MemorySegment = aeteResource(ObjCRuntime.newNSString(Arena.global(), languageName))
    
    // @property suiteNames
    /** @return NSArray<NSString *> * */
    open fun suiteNames(): MemorySegment {
        val sel = ObjCRuntime.sel("suiteNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _isLoadingSDEFFiles: BOOL
    // ivar: _isLoadingSecurityOverride: BOOL
    // ivar: _hasLoadedIntrinsics: BOOL
    // ivar: _reserved1: MemorySegment
    // ivar: _seenBundles: MemorySegment
    // ivar: _suiteDescriptionsBeingCollected: MemorySegment
    // ivar: _classDescriptionNeedingRegistration: MemorySegment
    // ivar: _suiteDescriptions: MemorySegment
    // ivar: _commandDescriptionNeedingRegistration: MemorySegment
    // ivar: _cachedClassDescriptionsByAppleEventCode: MemorySegment
    // ivar: _cachedCommandDescriptionsByAppleEventCodes: MemorySegment
    // ivar: _cachedSuiteDescriptionsByName: MemorySegment
    // ivar: _complexTypeDescriptionsByName: MemorySegment
    // ivar: _listTypeDescriptionsByName: MemorySegment
    // ivar: _nextComplexTypeAppleEventCode: Any
    // ivar: _reserved2: MemorySegment
}


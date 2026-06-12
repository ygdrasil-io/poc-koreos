package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScriptClassDescription
 * Superclass: NSClassDescription
 */
open class NSScriptClassDescription(ptr: MemorySegment) : NSClassDescription(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScriptClassDescription") }
        
        override fun `classDescriptionForClass`(aClass: Class<*>): MemorySegment {
            val sel = ObjCRuntime.sel("classDescriptionForClass:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, aClass) as MemorySegment
        }
        
    }
    
    fun initWithSuiteName_className_dictionary(suiteName: MemorySegment, className: MemorySegment, classDeclaration: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithSuiteName:className:dictionary:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, suiteName, className, classDeclaration) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithSuiteName_className_dictionary(suiteName: String, className: String, classDeclaration: MemorySegment): MemorySegment = initWithSuiteName_className_dictionary(ObjCRuntime.newNSString(Arena.global(), suiteName), ObjCRuntime.newNSString(Arena.global(), className), classDeclaration)
    
    fun matchesAppleEventCode(appleEventCode: FourCharCode): BOOL {
        val sel = ObjCRuntime.sel("matchesAppleEventCode:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, appleEventCode) as BOOL
    }
    
    fun supportsCommand(commandDescription: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("supportsCommand:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, commandDescription) as BOOL
    }
    
    fun selectorForCommand(commandDescription: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("selectorForCommand:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, commandDescription) as MemorySegment
    }
    
    fun typeForKey(key: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("typeForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun typeForKeyAsString(key: MemorySegment): String = ObjCRuntime.toJavaString(typeForKey(key))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun typeForKey(key: String): MemorySegment = typeForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun typeForKeyAsString(key: String): String = ObjCRuntime.toJavaString(typeForKey(ObjCRuntime.newNSString(Arena.global(), key)))
    
    fun classDescriptionForKey(key: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("classDescriptionForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun classDescriptionForKey(key: String): MemorySegment = classDescriptionForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    fun appleEventCodeForKey(key: MemorySegment): FourCharCode {
        val sel = ObjCRuntime.sel("appleEventCodeForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, key) as FourCharCode
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun appleEventCodeForKey(key: String): FourCharCode = appleEventCodeForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    fun keyWithAppleEventCode(appleEventCode: FourCharCode): MemorySegment {
        val sel = ObjCRuntime.sel("keyWithAppleEventCode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, appleEventCode) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun keyWithAppleEventCodeAsString(appleEventCode: FourCharCode): String = ObjCRuntime.toJavaString(keyWithAppleEventCode(appleEventCode))
    
    fun isLocationRequiredToCreateForKey(toManyRelationshipKey: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isLocationRequiredToCreateForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, toManyRelationshipKey) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun isLocationRequiredToCreateForKey(toManyRelationshipKey: String): BOOL = isLocationRequiredToCreateForKey(ObjCRuntime.newNSString(Arena.global(), toManyRelationshipKey))
    
    fun hasPropertyForKey(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("hasPropertyForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun hasPropertyForKey(key: String): BOOL = hasPropertyForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    fun hasOrderedToManyRelationshipForKey(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("hasOrderedToManyRelationshipForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun hasOrderedToManyRelationshipForKey(key: String): BOOL = hasOrderedToManyRelationshipForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    fun hasReadablePropertyForKey(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("hasReadablePropertyForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun hasReadablePropertyForKey(key: String): BOOL = hasReadablePropertyForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    fun hasWritablePropertyForKey(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("hasWritablePropertyForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun hasWritablePropertyForKey(key: String): BOOL = hasWritablePropertyForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    // @property suiteName
    fun suiteName(): MemorySegment {
        val sel = ObjCRuntime.sel("suiteName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun suiteNameAsString(): String = ObjCRuntime.toJavaString(suiteName())
    
    // @property className
    fun className(): MemorySegment {
        val sel = ObjCRuntime.sel("className")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun classNameAsString(): String = ObjCRuntime.toJavaString(className())
    
    // @property implementationClassName
    fun implementationClassName(): MemorySegment {
        val sel = ObjCRuntime.sel("implementationClassName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun implementationClassNameAsString(): String = ObjCRuntime.toJavaString(implementationClassName())
    
    // @property superclassDescription
    fun superclassDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("superclassDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property appleEventCode
    fun appleEventCode(): FourCharCode {
        val sel = ObjCRuntime.sel("appleEventCode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as FourCharCode
    }
    
    // @property defaultSubcontainerAttributeKey
    fun defaultSubcontainerAttributeKey(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultSubcontainerAttributeKey")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun defaultSubcontainerAttributeKeyAsString(): String = ObjCRuntime.toJavaString(defaultSubcontainerAttributeKey())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _suiteName: MemorySegment
    // ivar: _objcClassName: MemorySegment
    // ivar: _appleEventCode: FourCharCode
    // ivar: _superclassNameOrDescription: MemorySegment
    // ivar: _attributeDescriptions: MemorySegment
    // ivar: _toOneRelationshipDescriptions: MemorySegment
    // ivar: _toManyRelationshipDescriptions: MemorySegment
    // ivar: _commandMethodSelectorsByName: MemorySegment
    // ivar: _moreVars: MemorySegment
}

// ── Category: NSDeprecated on NSScriptClassDescription ─────────────────────────────────────────

fun NSScriptClassDescription.isReadOnlyKey(key: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isReadOnlyKey:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
}


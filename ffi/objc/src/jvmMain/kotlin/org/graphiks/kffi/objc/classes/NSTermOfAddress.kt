package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTermOfAddress
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSTermOfAddress(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTermOfAddress") }
        
        open fun neutral(): MemorySegment {
            val sel = ObjCRuntime.sel("neutral")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun feminine(): MemorySegment {
            val sel = ObjCRuntime.sel("feminine")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun masculine(): MemorySegment {
            val sel = ObjCRuntime.sel("masculine")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun currentUser(): MemorySegment {
            val sel = ObjCRuntime.sel("currentUser")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun localizedForLanguageIdentifier_withPronouns(language: MemorySegment, pronouns: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("localizedForLanguageIdentifier:withPronouns:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, language, pronouns) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun localizedForLanguageIdentifier_withPronouns(language: String, pronouns: MemorySegment): MemorySegment = localizedForLanguageIdentifier_withPronouns(ObjCRuntime.newNSString(Arena.global(), language), pronouns)
        
        open fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property languageIdentifier
    open fun languageIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("languageIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun languageIdentifierAsString(): String = ObjCRuntime.toJavaString(languageIdentifier())
    
    // @property pronouns
    /** @return NSArray<NSMorphologyPronoun *> * */
    open fun pronouns(): MemorySegment {
        val sel = ObjCRuntime.sel("pronouns")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


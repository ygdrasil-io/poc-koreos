package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSScriptObjectSpecifier
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSScriptObjectSpecifier(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScriptObjectSpecifier") }
        
        open fun objectSpecifierWithDescriptor(descriptor: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("objectSpecifierWithDescriptor:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, descriptor) as MemorySegment
        }
        
    }
    
    open fun initWithContainerSpecifier_key(container: MemorySegment, property: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContainerSpecifier:key:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, container, property) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithContainerSpecifier_key(container: MemorySegment, property: String): MemorySegment = initWithContainerSpecifier_key(container, ObjCRuntime.newNSString(Arena.global(), property))
    
    open fun initWithContainerClassDescription_containerSpecifier_key(classDesc: MemorySegment, container: MemorySegment, property: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContainerClassDescription:containerSpecifier:key:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, classDesc, container, property) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithContainerClassDescription_containerSpecifier_key(classDesc: MemorySegment, container: MemorySegment, property: String): MemorySegment = initWithContainerClassDescription_containerSpecifier_key(classDesc, container, ObjCRuntime.newNSString(Arena.global(), property))
    
    open fun initWithCoder(inCoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inCoder) as MemorySegment
    }
    
    open fun indicesOfObjectsByEvaluatingWithContainer_count(container: MemorySegment, count: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("indicesOfObjectsByEvaluatingWithContainer:count:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, container, count) as MemorySegment
    }
    
    open fun objectsByEvaluatingWithContainers(containers: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectsByEvaluatingWithContainers:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, containers) as MemorySegment
    }
    
    // @property childSpecifier
    open fun childSpecifier(): MemorySegment {
        val sel = ObjCRuntime.sel("childSpecifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setChildSpecifier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setChildSpecifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property containerSpecifier
    open fun containerSpecifier(): MemorySegment {
        val sel = ObjCRuntime.sel("containerSpecifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setContainerSpecifier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContainerSpecifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property containerIsObjectBeingTested
    open fun containerIsObjectBeingTested(): BOOL {
        val sel = ObjCRuntime.sel("containerIsObjectBeingTested")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setContainerIsObjectBeingTested(value: BOOL) {
        val sel = ObjCRuntime.sel("setContainerIsObjectBeingTested:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property containerIsRangeContainerObject
    open fun containerIsRangeContainerObject(): BOOL {
        val sel = ObjCRuntime.sel("containerIsRangeContainerObject")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setContainerIsRangeContainerObject(value: BOOL) {
        val sel = ObjCRuntime.sel("setContainerIsRangeContainerObject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property key
    open fun key(): MemorySegment {
        val sel = ObjCRuntime.sel("key")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setKey(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun keyAsString(): String = ObjCRuntime.toJavaString(key())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setKey(value: String) = setKey(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property containerClassDescription
    open fun containerClassDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("containerClassDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setContainerClassDescription(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContainerClassDescription:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property keyClassDescription
    open fun keyClassDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("keyClassDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property objectsByEvaluatingSpecifier
    open fun objectsByEvaluatingSpecifier(): MemorySegment {
        val sel = ObjCRuntime.sel("objectsByEvaluatingSpecifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property evaluationErrorNumber
    open fun evaluationErrorNumber(): NSInteger {
        val sel = ObjCRuntime.sel("evaluationErrorNumber")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    open fun setEvaluationErrorNumber(value: NSInteger) {
        val sel = ObjCRuntime.sel("setEvaluationErrorNumber:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property evaluationErrorSpecifier
    open fun evaluationErrorSpecifier(): MemorySegment {
        val sel = ObjCRuntime.sel("evaluationErrorSpecifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property descriptor
    open fun descriptor(): MemorySegment {
        val sel = ObjCRuntime.sel("descriptor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _container: MemorySegment
    // ivar: _child: MemorySegment
    // ivar: _key: MemorySegment
    // ivar: _containerClassDescription: MemorySegment
    // ivar: _containerIsObjectBeingTested: BOOL
    // ivar: _containerIsRangeContainerObject: BOOL
    // ivar: _padding: MemorySegment
    // ivar: _descriptor: MemorySegment
    // ivar: _error: NSInteger
}


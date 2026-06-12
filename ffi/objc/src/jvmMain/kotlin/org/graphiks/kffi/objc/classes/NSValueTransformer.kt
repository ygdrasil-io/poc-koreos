package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSValueTransformer
 * Superclass: NSObject
 */
open class NSValueTransformer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSValueTransformer") }
        
        open fun setValueTransformer_forName(transformer: MemorySegment, name: NSValueTransformerName): Unit {
            val sel = ObjCRuntime.sel("setValueTransformer:forName:")
            ObjCRuntime.msgSend(null, _class, sel, transformer, name)
        }
        
        open fun valueTransformerForName(name: NSValueTransformerName): MemorySegment {
            val sel = ObjCRuntime.sel("valueTransformerForName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** @return NSArray<NSValueTransformerName> * */
        open fun valueTransformerNames(): MemorySegment {
            val sel = ObjCRuntime.sel("valueTransformerNames")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun transformedValueClass(): Class<*> {
            val sel = ObjCRuntime.sel("transformedValueClass")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as Class<*>
        }
        
        open fun allowsReverseTransformation(): BOOL {
            val sel = ObjCRuntime.sel("allowsReverseTransformation")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    open fun transformedValue(value: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("transformedValue:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    open fun reverseTransformedValue(value: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("reverseTransformedValue:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
}


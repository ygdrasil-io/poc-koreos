/**
 * Kotlin/JVM wrapper for Objective-C class: NSValueTransformer
 * Superclass: NSObject
 */
open class NSValueTransformer(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSValueTransformer") }
        
        fun setValueTransformer_forName(transformer: MemorySegment, name: NSValueTransformerName): Unit {
            val sel = ObjCRuntime.sel("setValueTransformer:forName:")
            ObjCRuntime.msgSend(null, _class, sel, transformer, name)
        }
        
        fun valueTransformerForName(name: NSValueTransformerName): MemorySegment {
            val sel = ObjCRuntime.sel("valueTransformerForName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** @return NSArray<NSValueTransformerName> * */
        fun valueTransformerNames(): MemorySegment {
            val sel = ObjCRuntime.sel("valueTransformerNames")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun transformedValueClass(): Class {
            val sel = ObjCRuntime.sel("transformedValueClass")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as Class
        }
        
        fun allowsReverseTransformation(): BOOL {
            val sel = ObjCRuntime.sel("allowsReverseTransformation")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    fun transformedValue(value: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("transformedValue:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
    fun reverseTransformedValue(value: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("reverseTransformedValue:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value) as MemorySegment
    }
    
}


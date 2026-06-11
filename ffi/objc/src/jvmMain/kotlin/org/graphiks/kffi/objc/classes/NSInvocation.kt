/**
 * Kotlin/JVM wrapper for Objective-C class: NSInvocation
 * Superclass: NSObject
 */
open class NSInvocation(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSInvocation") }
        
        fun invocationWithMethodSignature(sig: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("invocationWithMethodSignature:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, sig) as MemorySegment
        }
        
    }
    
    fun retainArguments(): Unit {
        val sel = ObjCRuntime.sel("retainArguments")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun getReturnValue(retLoc: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getReturnValue:")
        ObjCRuntime.msgSend(null, ptr, sel, retLoc)
    }
    
    fun setReturnValue(retLoc: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setReturnValue:")
        ObjCRuntime.msgSend(null, ptr, sel, retLoc)
    }
    
    fun getArgument_atIndex(argumentLocation: MemorySegment, idx: NSInteger): Unit {
        val sel = ObjCRuntime.sel("getArgument:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, argumentLocation, idx)
    }
    
    fun setArgument_atIndex(argumentLocation: MemorySegment, idx: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setArgument:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, argumentLocation, idx)
    }
    
    fun invoke(): Unit {
        val sel = ObjCRuntime.sel("invoke")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun invokeWithTarget(target: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invokeWithTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, target)
    }
    
    fun invokeUsingIMP(imp: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("invokeUsingIMP:")
        ObjCRuntime.msgSend(null, ptr, sel, imp)
    }
    
    // @property methodSignature
    fun methodSignature(): MemorySegment {
        val sel = ObjCRuntime.sel("methodSignature")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property argumentsRetained
    fun argumentsRetained(): BOOL {
        val sel = ObjCRuntime.sel("argumentsRetained")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property target
    fun target(): MemorySegment {
        val sel = ObjCRuntime.sel("target")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTarget(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selector
    fun selector(): MemorySegment {
        val sel = ObjCRuntime.sel("selector")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSelector(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSelector:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


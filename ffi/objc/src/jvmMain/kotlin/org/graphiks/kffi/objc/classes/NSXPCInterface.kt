/**
 * Kotlin/JVM wrapper for Objective-C class: NSXPCInterface
 * Superclass: NSObject
 */
open class NSXPCInterface(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSXPCInterface") }
        
        fun interfaceWithProtocol(protocol: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("interfaceWithProtocol:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, protocol) as MemorySegment
        }
        
    }
    
    fun setClasses_forSelector_argumentIndex_ofReply(classes: MemorySegment, sel: MemorySegment, arg: NSUInteger, ofReply: BOOL): Unit {
        val sel = ObjCRuntime.sel("setClasses:forSelector:argumentIndex:ofReply:")
        ObjCRuntime.msgSend(null, ptr, sel, classes, sel, arg, ofReply)
    }
    
    /** @return NSSet<Class> * */
    fun classesForSelector_argumentIndex_ofReply(sel: MemorySegment, arg: NSUInteger, ofReply: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("classesForSelector:argumentIndex:ofReply:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sel, arg, ofReply) as MemorySegment
    }
    
    fun setInterface_forSelector_argumentIndex_ofReply(ifc: MemorySegment, sel: MemorySegment, arg: NSUInteger, ofReply: BOOL): Unit {
        val sel = ObjCRuntime.sel("setInterface:forSelector:argumentIndex:ofReply:")
        ObjCRuntime.msgSend(null, ptr, sel, ifc, sel, arg, ofReply)
    }
    
    fun interfaceForSelector_argumentIndex_ofReply(sel: MemorySegment, arg: NSUInteger, ofReply: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("interfaceForSelector:argumentIndex:ofReply:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sel, arg, ofReply) as MemorySegment
    }
    
    fun setXPCType_forSelector_argumentIndex_ofReply(type: MemorySegment, sel: MemorySegment, arg: NSUInteger, ofReply: BOOL): Unit {
        val sel = ObjCRuntime.sel("setXPCType:forSelector:argumentIndex:ofReply:")
        ObjCRuntime.msgSend(null, ptr, sel, type, sel, arg, ofReply)
    }
    
    fun XPCTypeForSelector_argumentIndex_ofReply(sel: MemorySegment, arg: NSUInteger, ofReply: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("XPCTypeForSelector:argumentIndex:ofReply:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sel, arg, ofReply) as MemorySegment
    }
    
    // @property protocol
    fun protocol(): MemorySegment {
        val sel = ObjCRuntime.sel("protocol")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setProtocol(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setProtocol:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


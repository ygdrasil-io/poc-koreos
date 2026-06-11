/**
 * Kotlin/JVM wrapper for Objective-C class: NSScriptWhoseTest
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSScriptWhoseTest(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScriptWhoseTest") }
        
    }
    
    fun isTrue(): BOOL {
        val sel = ObjCRuntime.sel("isTrue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(inCoder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, inCoder) as MemorySegment
    }
    
}


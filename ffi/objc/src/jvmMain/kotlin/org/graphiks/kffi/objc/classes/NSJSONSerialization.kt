/**
 * Kotlin/JVM wrapper for Objective-C class: NSJSONSerialization
 * Superclass: NSObject
 */
open class NSJSONSerialization(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSJSONSerialization") }
        
        fun isValidJSONObject(obj: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("isValidJSONObject:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, obj) as BOOL
        }
        
        fun dataWithJSONObject_options_error(obj: MemorySegment, opt: NSJSONWritingOptions, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dataWithJSONObject:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, obj, opt, error) as MemorySegment
        }
        
        fun JSONObjectWithData_options_error(`data`: MemorySegment, opt: NSJSONReadingOptions, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("JSONObjectWithData:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`, opt, error) as MemorySegment
        }
        
        fun writeJSONObject_toStream_options_error(obj: MemorySegment, stream: MemorySegment, opt: NSJSONWritingOptions, error: MemorySegment): NSInteger {
            val sel = ObjCRuntime.sel("writeJSONObject:toStream:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, _class, sel, obj, stream, opt, error) as NSInteger
        }
        
        fun JSONObjectWithStream_options_error(stream: MemorySegment, opt: NSJSONReadingOptions, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("JSONObjectWithStream:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, stream, opt, error) as MemorySegment
        }
        
    }
    
}


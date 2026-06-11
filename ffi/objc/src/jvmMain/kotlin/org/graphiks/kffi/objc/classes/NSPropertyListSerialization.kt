/**
 * Kotlin/JVM wrapper for Objective-C class: NSPropertyListSerialization
 * Superclass: NSObject
 */
open class NSPropertyListSerialization(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPropertyListSerialization") }
        
        fun propertyList_isValidForFormat(plist: MemorySegment, format: NSPropertyListFormat): BOOL {
            val sel = ObjCRuntime.sel("propertyList:isValidForFormat:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, plist, format) as BOOL
        }
        
        fun dataWithPropertyList_format_options_error(plist: MemorySegment, format: NSPropertyListFormat, opt: NSPropertyListWriteOptions, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dataWithPropertyList:format:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, plist, format, opt, error) as MemorySegment
        }
        
        fun writePropertyList_toStream_format_options_error(plist: MemorySegment, stream: MemorySegment, format: NSPropertyListFormat, opt: NSPropertyListWriteOptions, error: MemorySegment): NSInteger {
            val sel = ObjCRuntime.sel("writePropertyList:toStream:format:options:error:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, _class, sel, plist, stream, format, opt, error) as NSInteger
        }
        
        fun propertyListWithData_options_format_error(`data`: MemorySegment, opt: NSPropertyListReadOptions, format: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("propertyListWithData:options:format:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`, opt, format, error) as MemorySegment
        }
        
        fun propertyListWithStream_options_format_error(stream: MemorySegment, opt: NSPropertyListReadOptions, format: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("propertyListWithStream:options:format:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, stream, opt, format, error) as MemorySegment
        }
        
        fun dataFromPropertyList_format_errorDescription(plist: MemorySegment, format: NSPropertyListFormat, errorString: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("dataFromPropertyList:format:errorDescription:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, plist, format, errorString) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun dataFromPropertyList_format_errorDescription(plist: MemorySegment, format: NSPropertyListFormat, errorString: String): MemorySegment = dataFromPropertyList_format_errorDescription(plist, format, ObjCRuntime.newNSString(Arena.global(), errorString))
        
        fun propertyListFromData_mutabilityOption_format_errorDescription(`data`: MemorySegment, opt: NSPropertyListMutabilityOptions, format: MemorySegment, errorString: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("propertyListFromData:mutabilityOption:format:errorDescription:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`, opt, format, errorString) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun propertyListFromData_mutabilityOption_format_errorDescription(`data`: MemorySegment, opt: NSPropertyListMutabilityOptions, format: MemorySegment, errorString: String): MemorySegment = propertyListFromData_mutabilityOption_format_errorDescription(`data`, opt, format, ObjCRuntime.newNSString(Arena.global(), errorString))
        
    }
    
}


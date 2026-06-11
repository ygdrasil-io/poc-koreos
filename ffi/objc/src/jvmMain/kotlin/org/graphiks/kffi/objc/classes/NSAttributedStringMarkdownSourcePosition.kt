/**
 * Kotlin/JVM wrapper for Objective-C class: NSAttributedStringMarkdownSourcePosition
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSAttributedStringMarkdownSourcePosition(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAttributedStringMarkdownSourcePosition") }
        
    }
    
    fun initWithStartLine_startColumn_endLine_endColumn(startLine: NSInteger, startColumn: NSInteger, endLine: NSInteger, endColumn: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithStartLine:startColumn:endLine:endColumn:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, startLine, startColumn, endLine, endColumn) as MemorySegment
    }
    
    fun rangeInString(string: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("rangeInString:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, string) as NSRange
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun rangeInString(string: String): NSRange = rangeInString(ObjCRuntime.newNSString(Arena.global(), string))
    
    // @property startLine
    fun startLine(): NSInteger {
        val sel = ObjCRuntime.sel("startLine")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property startColumn
    fun startColumn(): NSInteger {
        val sel = ObjCRuntime.sel("startColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property endLine
    fun endLine(): NSInteger {
        val sel = ObjCRuntime.sel("endLine")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property endColumn
    fun endColumn(): NSInteger {
        val sel = ObjCRuntime.sel("endColumn")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
}


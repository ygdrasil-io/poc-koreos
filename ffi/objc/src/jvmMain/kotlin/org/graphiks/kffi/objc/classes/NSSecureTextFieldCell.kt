/**
 * Kotlin/JVM wrapper for Objective-C class: NSSecureTextFieldCell
 * Superclass: NSTextFieldCell
 */
open class NSSecureTextFieldCell(ptr: MemorySegment) : NSTextFieldCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSecureTextFieldCell") }
        
    }
    
    // @property echosBullets
    fun echosBullets(): BOOL {
        val sel = ObjCRuntime.sel("echosBullets")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEchosBullets(value: BOOL) {
        val sel = ObjCRuntime.sel("setEchosBullets:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


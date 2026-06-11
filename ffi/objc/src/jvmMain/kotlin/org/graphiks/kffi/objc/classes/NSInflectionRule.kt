/**
 * Kotlin/JVM wrapper for Objective-C class: NSInflectionRule
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSInflectionRule(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSInflectionRule") }
        
        fun automaticRule(): MemorySegment {
            val sel = ObjCRuntime.sel("automaticRule")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property automaticRule
    fun automaticRule(): MemorySegment {
        val sel = ObjCRuntime.sel("automaticRule")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

// ── Category: NSInflectionAvailability on NSInflectionRule ─────────────────────────────────────────

// Class method: +[NSInflectionRule canInflectLanguage:]
fun NSInflectionRule_canInflectLanguage(language: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("canInflectLanguage:")
    val cls = ObjCRuntime.getClass("NSInflectionRule")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel, language) as BOOL
}

// Class method: +[NSInflectionRule canInflectPreferredLocalization]
fun NSInflectionRule_canInflectPreferredLocalization(): BOOL {
    val sel = ObjCRuntime.sel("canInflectPreferredLocalization")
    val cls = ObjCRuntime.getClass("NSInflectionRule")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel) as BOOL
}

// @property canInflectPreferredLocalization
fun NSInflectionRule.canInflectPreferredLocalization(): BOOL {
    val sel = ObjCRuntime.sel("canInflectPreferredLocalization")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}


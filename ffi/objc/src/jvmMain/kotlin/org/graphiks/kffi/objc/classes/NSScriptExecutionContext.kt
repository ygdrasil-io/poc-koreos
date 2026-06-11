/**
 * Kotlin/JVM wrapper for Objective-C class: NSScriptExecutionContext
 * Superclass: NSObject
 */
open class NSScriptExecutionContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSScriptExecutionContext") }
        
        fun sharedScriptExecutionContext(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedScriptExecutionContext")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property topLevelObject
    fun topLevelObject(): MemorySegment {
        val sel = ObjCRuntime.sel("topLevelObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTopLevelObject(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTopLevelObject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property objectBeingTested
    fun objectBeingTested(): MemorySegment {
        val sel = ObjCRuntime.sel("objectBeingTested")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setObjectBeingTested(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setObjectBeingTested:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rangeContainerObject
    fun rangeContainerObject(): MemorySegment {
        val sel = ObjCRuntime.sel("rangeContainerObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRangeContainerObject(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRangeContainerObject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _topLevelObject: MemorySegment
    // ivar: _objectBeingTested: MemorySegment
    // ivar: _rangeContainerObject: MemorySegment
    // ivar: _moreVars: MemorySegment
}


/**
 * Kotlin/JVM wrapper for Objective-C class: NSController
 * Superclass: NSObject
 * Protocols: NSCoding, NSEditor, NSEditorRegistration
 */
open class NSController(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSController") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun objectDidBeginEditing(editor: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("objectDidBeginEditing:")
        ObjCRuntime.msgSend(null, ptr, sel, editor)
    }
    
    fun objectDidEndEditing(editor: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("objectDidEndEditing:")
        ObjCRuntime.msgSend(null, ptr, sel, editor)
    }
    
    fun discardEditing(): Unit {
        val sel = ObjCRuntime.sel("discardEditing")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun commitEditing(): BOOL {
        val sel = ObjCRuntime.sel("commitEditing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun commitEditingWithDelegate_didCommitSelector_contextInfo(delegate: MemorySegment, didCommitSelector: MemorySegment, contextInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("commitEditingWithDelegate:didCommitSelector:contextInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, delegate, didCommitSelector, contextInfo)
    }
    
    // @property editing
    fun isEditing(): BOOL {
        val sel = ObjCRuntime.sel("isEditing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}


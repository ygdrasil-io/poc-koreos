/**
 * Kotlin/JVM wrapper for Objective-C class: NSNib
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSNib(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSNib") }
        
    }
    
    fun initWithNibNamed_bundle(nibName: NSNibName, bundle: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithNibNamed:bundle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, nibName, bundle) as MemorySegment
    }
    
    fun initWithNibData_bundle(nibData: MemorySegment, bundle: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithNibData:bundle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, nibData, bundle) as MemorySegment
    }
    
    fun instantiateWithOwner_topLevelObjects(owner: MemorySegment, topLevelObjects: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("instantiateWithOwner:topLevelObjects:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, owner, topLevelObjects) as BOOL
    }
    
}

// ── Category: NSDeprecated on NSNib ─────────────────────────────────────────

fun NSNib.initWithContentsOfURL(nibFileURL: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, nibFileURL) as MemorySegment
}

fun NSNib.instantiateNibWithExternalNameTable(externalNameTable: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("instantiateNibWithExternalNameTable:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, externalNameTable) as BOOL
}

fun NSNib.instantiateNibWithOwner_topLevelObjects(owner: MemorySegment, topLevelObjects: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("instantiateNibWithOwner:topLevelObjects:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, owner, topLevelObjects) as BOOL
}


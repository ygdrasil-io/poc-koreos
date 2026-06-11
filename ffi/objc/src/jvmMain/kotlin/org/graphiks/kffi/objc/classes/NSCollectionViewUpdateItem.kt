/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewUpdateItem
 * Superclass: NSObject
 */
open class NSCollectionViewUpdateItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewUpdateItem") }
        
    }
    
    // @property indexPathBeforeUpdate
    fun indexPathBeforeUpdate(): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathBeforeUpdate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property indexPathAfterUpdate
    fun indexPathAfterUpdate(): MemorySegment {
        val sel = ObjCRuntime.sel("indexPathAfterUpdate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property updateAction
    fun updateAction(): NSCollectionUpdateAction {
        val sel = ObjCRuntime.sel("updateAction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCollectionUpdateAction
    }
    
}


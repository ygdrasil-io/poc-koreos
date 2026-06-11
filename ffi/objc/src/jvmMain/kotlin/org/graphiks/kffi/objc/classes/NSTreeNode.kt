/**
 * Kotlin/JVM wrapper for Objective-C class: NSTreeNode
 * Superclass: NSObject
 */
open class NSTreeNode(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTreeNode") }
        
        fun treeNodeWithRepresentedObject(modelObject: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("treeNodeWithRepresentedObject:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, modelObject) as MemorySegment
        }
        
    }
    
    fun initWithRepresentedObject(modelObject: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRepresentedObject:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, modelObject) as MemorySegment
    }
    
    fun descendantNodeAtIndexPath(indexPath: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("descendantNodeAtIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexPath) as MemorySegment
    }
    
    fun sortWithSortDescriptors_recursively(sortDescriptors: MemorySegment, recursively: BOOL): Unit {
        val sel = ObjCRuntime.sel("sortWithSortDescriptors:recursively:")
        ObjCRuntime.msgSend(null, ptr, sel, sortDescriptors, recursively)
    }
    
    // @property representedObject
    fun representedObject(): MemorySegment {
        val sel = ObjCRuntime.sel("representedObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property indexPath
    fun indexPath(): MemorySegment {
        val sel = ObjCRuntime.sel("indexPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property leaf
    fun isLeaf(): BOOL {
        val sel = ObjCRuntime.sel("isLeaf")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property childNodes
    /** @return NSArray<NSTreeNode *> * */
    fun childNodes(): MemorySegment {
        val sel = ObjCRuntime.sel("childNodes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property mutableChildNodes
    /** @return NSMutableArray<NSTreeNode *> * */
    fun mutableChildNodes(): MemorySegment {
        val sel = ObjCRuntime.sel("mutableChildNodes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property parentNode
    fun parentNode(): MemorySegment {
        val sel = ObjCRuntime.sel("parentNode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


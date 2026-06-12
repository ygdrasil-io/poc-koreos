package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTreeController
 * Superclass: NSObjectController
 */
open class NSTreeController(ptr: MemorySegment) : NSObjectController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTreeController") }
        
    }
    
    fun rearrangeObjects(): Unit {
        val sel = ObjCRuntime.sel("rearrangeObjects")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    override fun `add`(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("add:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    override fun `remove`(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("remove:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun addChild(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addChild:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun insert(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insert:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun insertChild(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertChild:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun insertObject_atArrangedObjectIndexPath(`object`: MemorySegment, indexPath: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertObject:atArrangedObjectIndexPath:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`, indexPath)
    }
    
    fun insertObjects_atArrangedObjectIndexPaths(objects: MemorySegment, indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertObjects:atArrangedObjectIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, objects, indexPaths)
    }
    
    fun removeObjectAtArrangedObjectIndexPath(indexPath: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObjectAtArrangedObjectIndexPath:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPath)
    }
    
    fun removeObjectsAtArrangedObjectIndexPaths(indexPaths: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObjectsAtArrangedObjectIndexPaths:")
        ObjCRuntime.msgSend(null, ptr, sel, indexPaths)
    }
    
    fun setSelectionIndexPaths(indexPaths: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setSelectionIndexPaths:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexPaths) as BOOL
    }
    
    fun setSelectionIndexPath(indexPath: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setSelectionIndexPath:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexPath) as BOOL
    }
    
    fun addSelectionIndexPaths(indexPaths: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("addSelectionIndexPaths:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexPaths) as BOOL
    }
    
    fun removeSelectionIndexPaths(indexPaths: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeSelectionIndexPaths:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexPaths) as BOOL
    }
    
    fun moveNode_toIndexPath(node: MemorySegment, indexPath: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveNode:toIndexPath:")
        ObjCRuntime.msgSend(null, ptr, sel, node, indexPath)
    }
    
    fun moveNodes_toIndexPath(nodes: MemorySegment, startingIndexPath: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("moveNodes:toIndexPath:")
        ObjCRuntime.msgSend(null, ptr, sel, nodes, startingIndexPath)
    }
    
    fun childrenKeyPathForNode(node: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("childrenKeyPathForNode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, node) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun childrenKeyPathForNodeAsString(node: MemorySegment): String = ObjCRuntime.toJavaString(childrenKeyPathForNode(node))
    
    fun countKeyPathForNode(node: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("countKeyPathForNode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, node) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun countKeyPathForNodeAsString(node: MemorySegment): String = ObjCRuntime.toJavaString(countKeyPathForNode(node))
    
    fun leafKeyPathForNode(node: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("leafKeyPathForNode:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, node) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun leafKeyPathForNodeAsString(node: MemorySegment): String = ObjCRuntime.toJavaString(leafKeyPathForNode(node))
    
    // @property arrangedObjects
    fun arrangedObjects(): MemorySegment {
        val sel = ObjCRuntime.sel("arrangedObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property childrenKeyPath
    fun childrenKeyPath(): MemorySegment {
        val sel = ObjCRuntime.sel("childrenKeyPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setChildrenKeyPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setChildrenKeyPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun childrenKeyPathAsString(): String = ObjCRuntime.toJavaString(childrenKeyPath())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setChildrenKeyPath(value: String) = setChildrenKeyPath(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property countKeyPath
    fun countKeyPath(): MemorySegment {
        val sel = ObjCRuntime.sel("countKeyPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCountKeyPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCountKeyPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun countKeyPathAsString(): String = ObjCRuntime.toJavaString(countKeyPath())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCountKeyPath(value: String) = setCountKeyPath(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property leafKeyPath
    fun leafKeyPath(): MemorySegment {
        val sel = ObjCRuntime.sel("leafKeyPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLeafKeyPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLeafKeyPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun leafKeyPathAsString(): String = ObjCRuntime.toJavaString(leafKeyPath())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setLeafKeyPath(value: String) = setLeafKeyPath(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property sortDescriptors
    /** @return NSArray<NSSortDescriptor *> * */
    fun sortDescriptors(): MemorySegment {
        val sel = ObjCRuntime.sel("sortDescriptors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSortDescriptors(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSortDescriptors:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property content
    override fun `content`(): MemorySegment {
        val sel = ObjCRuntime.sel("content")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    override fun `setContent`(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canInsert
    fun canInsert(): BOOL {
        val sel = ObjCRuntime.sel("canInsert")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canInsertChild
    fun canInsertChild(): BOOL {
        val sel = ObjCRuntime.sel("canInsertChild")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canAddChild
    fun canAddChild(): BOOL {
        val sel = ObjCRuntime.sel("canAddChild")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property avoidsEmptySelection
    fun avoidsEmptySelection(): BOOL {
        val sel = ObjCRuntime.sel("avoidsEmptySelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAvoidsEmptySelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setAvoidsEmptySelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preservesSelection
    fun preservesSelection(): BOOL {
        val sel = ObjCRuntime.sel("preservesSelection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPreservesSelection(value: BOOL) {
        val sel = ObjCRuntime.sel("setPreservesSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectsInsertedObjects
    fun selectsInsertedObjects(): BOOL {
        val sel = ObjCRuntime.sel("selectsInsertedObjects")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelectsInsertedObjects(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelectsInsertedObjects:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alwaysUsesMultipleValuesMarker
    fun alwaysUsesMultipleValuesMarker(): BOOL {
        val sel = ObjCRuntime.sel("alwaysUsesMultipleValuesMarker")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAlwaysUsesMultipleValuesMarker(value: BOOL) {
        val sel = ObjCRuntime.sel("setAlwaysUsesMultipleValuesMarker:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectedObjects
    override fun `selectedObjects`(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectionIndexPaths
    /** @return NSArray<NSIndexPath *> * */
    fun selectionIndexPaths(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionIndexPaths")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectionIndexPath
    fun selectionIndexPath(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionIndexPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedNodes
    /** @return NSArray<NSTreeNode *> * */
    fun selectedNodes(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedNodes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


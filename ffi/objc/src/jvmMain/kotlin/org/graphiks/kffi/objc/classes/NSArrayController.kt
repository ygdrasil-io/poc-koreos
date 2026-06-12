package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSArrayController
 * Superclass: NSObjectController
 */
open class NSArrayController(ptr: MemorySegment) : NSObjectController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSArrayController") }
        
    }
    
    fun rearrangeObjects(): Unit {
        val sel = ObjCRuntime.sel("rearrangeObjects")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun didChangeArrangementCriteria(): Unit {
        val sel = ObjCRuntime.sel("didChangeArrangementCriteria")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun arrangeObjects(objects: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("arrangeObjects:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, objects) as MemorySegment
    }
    
    fun setSelectionIndexes(indexes: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setSelectionIndexes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexes) as BOOL
    }
    
    fun setSelectionIndex(index: NSUInteger): BOOL {
        val sel = ObjCRuntime.sel("setSelectionIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, index) as BOOL
    }
    
    fun addSelectionIndexes(indexes: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("addSelectionIndexes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexes) as BOOL
    }
    
    fun removeSelectionIndexes(indexes: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeSelectionIndexes:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, indexes) as BOOL
    }
    
    fun setSelectedObjects(objects: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setSelectedObjects:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, objects) as BOOL
    }
    
    fun addSelectedObjects(objects: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("addSelectedObjects:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, objects) as BOOL
    }
    
    fun removeSelectedObjects(objects: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeSelectedObjects:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, objects) as BOOL
    }
    
    override fun `add`(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("add:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    override fun `remove`(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("remove:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun insert(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insert:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectNext(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectNext:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun selectPrevious(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectPrevious:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    override fun `addObject`(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun addObjects(objects: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObjects:")
        ObjCRuntime.msgSend(null, ptr, sel, objects)
    }
    
    fun insertObject_atArrangedObjectIndex(`object`: MemorySegment, index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("insertObject:atArrangedObjectIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`, index)
    }
    
    fun insertObjects_atArrangedObjectIndexes(objects: MemorySegment, indexes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertObjects:atArrangedObjectIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, objects, indexes)
    }
    
    fun removeObjectAtArrangedObjectIndex(index: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("removeObjectAtArrangedObjectIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun removeObjectsAtArrangedObjectIndexes(indexes: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObjectsAtArrangedObjectIndexes:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes)
    }
    
    override fun `removeObject`(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun removeObjects(objects: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObjects:")
        ObjCRuntime.msgSend(null, ptr, sel, objects)
    }
    
    // @property automaticallyRearrangesObjects
    fun automaticallyRearrangesObjects(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyRearrangesObjects")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallyRearrangesObjects(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyRearrangesObjects:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property automaticRearrangementKeyPaths
    /** @return NSArray<NSString *> * */
    fun automaticRearrangementKeyPaths(): MemorySegment {
        val sel = ObjCRuntime.sel("automaticRearrangementKeyPaths")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
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
    
    // @property filterPredicate
    fun filterPredicate(): MemorySegment {
        val sel = ObjCRuntime.sel("filterPredicate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFilterPredicate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFilterPredicate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property clearsFilterPredicateOnInsertion
    fun clearsFilterPredicateOnInsertion(): BOOL {
        val sel = ObjCRuntime.sel("clearsFilterPredicateOnInsertion")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setClearsFilterPredicateOnInsertion(value: BOOL) {
        val sel = ObjCRuntime.sel("setClearsFilterPredicateOnInsertion:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property arrangedObjects
    fun arrangedObjects(): MemorySegment {
        val sel = ObjCRuntime.sel("arrangedObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property selectionIndexes
    fun selectionIndexes(): MemorySegment {
        val sel = ObjCRuntime.sel("selectionIndexes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectionIndex
    fun selectionIndex(): NSUInteger {
        val sel = ObjCRuntime.sel("selectionIndex")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property selectedObjects
    override fun `selectedObjects`(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property canInsert
    fun canInsert(): BOOL {
        val sel = ObjCRuntime.sel("canInsert")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canSelectNext
    fun canSelectNext(): BOOL {
        val sel = ObjCRuntime.sel("canSelectNext")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canSelectPrevious
    fun canSelectPrevious(): BOOL {
        val sel = ObjCRuntime.sel("canSelectPrevious")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}


/**
 * Kotlin/JVM wrapper for Objective-C class: NSObjectController
 * Superclass: NSController
 */
open class NSObjectController(ptr: MemorySegment) : NSController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSObjectController") }
        
    }
    
    fun initWithContent(content: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContent:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, content) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun prepareContent(): Unit {
        val sel = ObjCRuntime.sel("prepareContent")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun newObject(): MemorySegment {
        val sel = ObjCRuntime.sel("newObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun addObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun removeObject(`object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeObject:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`)
    }
    
    fun add(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("add:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun remove(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("remove:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun validateUserInterfaceItem(item: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("validateUserInterfaceItem:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, item) as BOOL
    }
    
    // @property content
    fun content(): MemorySegment {
        val sel = ObjCRuntime.sel("content")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setContent(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selection
    fun selection(): MemorySegment {
        val sel = ObjCRuntime.sel("selection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedObjects
    fun selectedObjects(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property automaticallyPreparesContent
    fun automaticallyPreparesContent(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyPreparesContent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallyPreparesContent(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyPreparesContent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property objectClass
    fun objectClass(): Class {
        val sel = ObjCRuntime.sel("objectClass")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
    }
    fun setObjectClass(value: Class) {
        val sel = ObjCRuntime.sel("setObjectClass:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property editable
    fun isEditable(): BOOL {
        val sel = ObjCRuntime.sel("isEditable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEditable(value: BOOL) {
        val sel = ObjCRuntime.sel("setEditable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canAdd
    fun canAdd(): BOOL {
        val sel = ObjCRuntime.sel("canAdd")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canRemove
    fun canRemove(): BOOL {
        val sel = ObjCRuntime.sel("canRemove")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSManagedController on NSObjectController ─────────────────────────────────────────

fun NSObjectController.fetchWithRequest_merge_error(fetchRequest: MemorySegment, merge: BOOL, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("fetchWithRequest:merge:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fetchRequest, merge, error) as BOOL
}

fun NSObjectController.fetch(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("fetch:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSObjectController.defaultFetchRequest(): MemorySegment {
    val sel = ObjCRuntime.sel("defaultFetchRequest")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObjectController.managedObjectContext(): MemorySegment {
    val sel = ObjCRuntime.sel("managedObjectContext")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObjectController.setManagedObjectContext(managedObjectContext: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setManagedObjectContext:")
    ObjCRuntime.msgSend(null, ptr, sel, managedObjectContext)
}

fun NSObjectController.entityName(): MemorySegment {
    val sel = ObjCRuntime.sel("entityName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObjectController.setEntityName(entityName: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setEntityName:")
    ObjCRuntime.msgSend(null, ptr, sel, entityName)
}

fun NSObjectController.fetchPredicate(): MemorySegment {
    val sel = ObjCRuntime.sel("fetchPredicate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObjectController.setFetchPredicate(fetchPredicate: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setFetchPredicate:")
    ObjCRuntime.msgSend(null, ptr, sel, fetchPredicate)
}

fun NSObjectController.usesLazyFetching(): BOOL {
    val sel = ObjCRuntime.sel("usesLazyFetching")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSObjectController.setUsesLazyFetching(usesLazyFetching: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesLazyFetching:")
    ObjCRuntime.msgSend(null, ptr, sel, usesLazyFetching)
}

// @property managedObjectContext
fun NSObjectController.managedObjectContext(): MemorySegment {
    val sel = ObjCRuntime.sel("managedObjectContext")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSObjectController.setManagedObjectContext(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setManagedObjectContext:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property entityName
fun NSObjectController.entityName(): MemorySegment {
    val sel = ObjCRuntime.sel("entityName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSObjectController.setEntityName(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setEntityName:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property fetchPredicate
fun NSObjectController.fetchPredicate(): MemorySegment {
    val sel = ObjCRuntime.sel("fetchPredicate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSObjectController.setFetchPredicate(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setFetchPredicate:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property usesLazyFetching
fun NSObjectController.usesLazyFetching(): BOOL {
    val sel = ObjCRuntime.sel("usesLazyFetching")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSObjectController.setUsesLazyFetching(value: BOOL) {
    val sel = ObjCRuntime.sel("setUsesLazyFetching:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}


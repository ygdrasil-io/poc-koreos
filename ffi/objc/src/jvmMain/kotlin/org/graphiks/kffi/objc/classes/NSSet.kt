/**
 * Kotlin/JVM wrapper for Objective-C class: NSSet
 * Superclass: NSObject
 * Protocols: NSCopying, NSMutableCopying, NSSecureCoding, NSFastEnumeration
 */
open class NSSet(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSet") }
        
    }
    
    fun member(`object`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("member:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `object`) as MemorySegment
    }
    
    /** @return NSEnumerator<ObjectType> * */
    fun objectEnumerator(): MemorySegment {
        val sel = ObjCRuntime.sel("objectEnumerator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithObjects_count(objects: MemorySegment, cnt: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObjects:count:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, objects, cnt) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    // @property count
    fun count(): NSUInteger {
        val sel = ObjCRuntime.sel("count")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
}

// ── Category: NSExtendedSet on NSSet ─────────────────────────────────────────

fun NSSet.anyObject(): MemorySegment {
    val sel = ObjCRuntime.sel("anyObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSet.containsObject(anObject: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("containsObject:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, anObject) as BOOL
}

fun NSSet.descriptionWithLocale(locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionWithLocale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
}

fun NSSet.intersectsSet(otherSet: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("intersectsSet:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, otherSet) as BOOL
}

fun NSSet.isEqualToSet(otherSet: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEqualToSet:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, otherSet) as BOOL
}

fun NSSet.isSubsetOfSet(otherSet: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isSubsetOfSet:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, otherSet) as BOOL
}

fun NSSet.makeObjectsPerformSelector(aSelector: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("makeObjectsPerformSelector:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector)
}

fun NSSet.makeObjectsPerformSelector_withObject(aSelector: MemorySegment, argument: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("makeObjectsPerformSelector:withObject:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, argument)
}

/** @return NSSet<ObjectType> * */
fun NSSet.setByAddingObject(anObject: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setByAddingObject:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anObject) as MemorySegment
}

/** @return NSSet<ObjectType> * */
fun NSSet.setByAddingObjectsFromSet(other: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setByAddingObjectsFromSet:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, other) as MemorySegment
}

/** @return NSSet<ObjectType> * */
fun NSSet.setByAddingObjectsFromArray(other: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setByAddingObjectsFromArray:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, other) as MemorySegment
}

fun NSSet.enumerateObjectsUsingBlock(block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateObjectsUsingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, block)
}

fun NSSet.enumerateObjectsWithOptions_usingBlock(opts: NSEnumerationOptions, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateObjectsWithOptions:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, opts, block)
}

/** @return NSSet<ObjectType> * */
fun NSSet.objectsPassingTest(predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("objectsPassingTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, predicate) as MemorySegment
}

/** @return NSSet<ObjectType> * */
fun NSSet.objectsWithOptions_passingTest(opts: NSEnumerationOptions, predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("objectsWithOptions:passingTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, opts, predicate) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSSet.allObjects(): MemorySegment {
    val sel = ObjCRuntime.sel("allObjects")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSSet.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property allObjects
/** @return NSArray<ObjectType> * */
fun NSSet.allObjects(): MemorySegment {
    val sel = ObjCRuntime.sel("allObjects")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property description
fun NSSet.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSSetCreation on NSSet ─────────────────────────────────────────

fun NSSet.initWithObjects(firstObj: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithObjects:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, firstObj) as MemorySegment
}

fun NSSet.initWithSet(`set`: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithSet:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `set`) as MemorySegment
}

fun NSSet.initWithSet_copyItems(`set`: MemorySegment, flag: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("initWithSet:copyItems:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `set`, flag) as MemorySegment
}

fun NSSet.initWithArray(array: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithArray:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, array) as MemorySegment
}

// Class method: +[NSSet set]
fun NSSet_`set`(): MemorySegment {
    val sel = ObjCRuntime.sel("set")
    val cls = ObjCRuntime.getClass("NSSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSSet setWithObject:]
fun NSSet_setWithObject(`object`: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setWithObject:")
    val cls = ObjCRuntime.getClass("NSSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, `object`) as MemorySegment
}

// Class method: +[NSSet setWithObjects:count:]
fun NSSet_setWithObjects_count(objects: MemorySegment, cnt: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("setWithObjects:count:")
    val cls = ObjCRuntime.getClass("NSSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, objects, cnt) as MemorySegment
}

// Class method: +[NSSet setWithObjects:]
fun NSSet_setWithObjects(firstObj: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setWithObjects:")
    val cls = ObjCRuntime.getClass("NSSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, firstObj) as MemorySegment
}

// Class method: +[NSSet setWithSet:]
fun NSSet_setWithSet(`set`: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setWithSet:")
    val cls = ObjCRuntime.getClass("NSSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, `set`) as MemorySegment
}

// Class method: +[NSSet setWithArray:]
fun NSSet_setWithArray(array: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setWithArray:")
    val cls = ObjCRuntime.getClass("NSSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, array) as MemorySegment
}

// ── Category: NSKeyValueCoding on NSSet ─────────────────────────────────────────

fun NSSet.valueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSSet.setValue_forKey(value: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setValue:forKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, key)
}

// ── Category: NSKeyValueObserverRegistration on NSSet ─────────────────────────────────────────

fun NSSet.addObserver_forKeyPath_options_context(observer: MemorySegment, keyPath: MemorySegment, options: NSKeyValueObservingOptions, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addObserver:forKeyPath:options:context:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath, options, context)
}

fun NSSet.removeObserver_forKeyPath_context(observer: MemorySegment, keyPath: MemorySegment, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObserver:forKeyPath:context:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath, context)
}

fun NSSet.removeObserver_forKeyPath(observer: MemorySegment, keyPath: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObserver:forKeyPath:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath)
}

// ── Category: NSSortDescriptorSorting on NSSet ─────────────────────────────────────────

/** @return NSArray<ObjectType> * */
fun NSSet.sortedArrayUsingDescriptors(sortDescriptors: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayUsingDescriptors:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sortDescriptors) as MemorySegment
}

// ── Category: NSPredicateSupport on NSSet ─────────────────────────────────────────

/** @return NSSet<ObjectType> * */
fun NSSet.filteredSetUsingPredicate(predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("filteredSetUsingPredicate:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, predicate) as MemorySegment
}

// ── Category: NSCollectionViewAdditions on NSSet ─────────────────────────────────────────

fun NSSet.enumerateIndexPathsWithOptions_usingBlock(opts: NSEnumerationOptions, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateIndexPathsWithOptions:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, opts, block)
}

// Class method: +[NSSet setWithCollectionViewIndexPath:]
fun NSSet_setWithCollectionViewIndexPath(indexPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setWithCollectionViewIndexPath:")
    val cls = ObjCRuntime.getClass("NSSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, indexPath) as MemorySegment
}

// Class method: +[NSSet setWithCollectionViewIndexPaths:]
fun NSSet_setWithCollectionViewIndexPaths(indexPaths: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("setWithCollectionViewIndexPaths:")
    val cls = ObjCRuntime.getClass("NSSet")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, indexPaths) as MemorySegment
}


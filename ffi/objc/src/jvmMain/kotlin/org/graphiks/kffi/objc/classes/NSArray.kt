/**
 * Kotlin/JVM wrapper for Objective-C class: NSArray
 * Superclass: NSObject
 * Protocols: NSCopying, NSMutableCopying, NSSecureCoding, NSFastEnumeration
 */
open class NSArray(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSArray") }
        
    }
    
    fun objectAtIndex(index: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("objectAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
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

// ── Category: NSExtendedArray on NSArray ─────────────────────────────────────────

/** @return NSArray<ObjectType> * */
fun NSArray.arrayByAddingObject(anObject: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayByAddingObject:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anObject) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.arrayByAddingObjectsFromArray(otherArray: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayByAddingObjectsFromArray:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherArray) as MemorySegment
}

fun NSArray.componentsJoinedByString(separator: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("componentsJoinedByString:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, separator) as MemorySegment
}

fun NSArray.containsObject(anObject: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("containsObject:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, anObject) as BOOL
}

fun NSArray.descriptionWithLocale(locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionWithLocale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
}

fun NSArray.descriptionWithLocale_indent(locale: MemorySegment, level: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionWithLocale:indent:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale, level) as MemorySegment
}

fun NSArray.firstObjectCommonWithArray(otherArray: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("firstObjectCommonWithArray:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherArray) as MemorySegment
}

fun NSArray.getObjects_range(objects: MemorySegment, range: NSRange): Unit {
    val sel = ObjCRuntime.sel("getObjects:range:")
    ObjCRuntime.msgSend(null, ptr, sel, objects, range)
}

fun NSArray.indexOfObject(anObject: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("indexOfObject:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, anObject) as NSUInteger
}

fun NSArray.indexOfObject_inRange(anObject: MemorySegment, range: NSRange): NSUInteger {
    val sel = ObjCRuntime.sel("indexOfObject:inRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, anObject, range) as NSUInteger
}

fun NSArray.indexOfObjectIdenticalTo(anObject: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("indexOfObjectIdenticalTo:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, anObject) as NSUInteger
}

fun NSArray.indexOfObjectIdenticalTo_inRange(anObject: MemorySegment, range: NSRange): NSUInteger {
    val sel = ObjCRuntime.sel("indexOfObjectIdenticalTo:inRange:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, anObject, range) as NSUInteger
}

fun NSArray.isEqualToArray(otherArray: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEqualToArray:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, otherArray) as BOOL
}

/** @return NSEnumerator<ObjectType> * */
fun NSArray.objectEnumerator(): MemorySegment {
    val sel = ObjCRuntime.sel("objectEnumerator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSEnumerator<ObjectType> * */
fun NSArray.reverseObjectEnumerator(): MemorySegment {
    val sel = ObjCRuntime.sel("reverseObjectEnumerator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.sortedArrayUsingFunction_context(comparator: MemorySegment, context: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayUsingFunction:context:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, comparator, context) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.sortedArrayUsingFunction_context_hint(comparator: MemorySegment, context: MemorySegment, hint: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayUsingFunction:context:hint:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, comparator, context, hint) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.sortedArrayUsingSelector(comparator: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayUsingSelector:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, comparator) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.subarrayWithRange(range: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("subarrayWithRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, range) as MemorySegment
}

fun NSArray.writeToURL_error(url: MemorySegment, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("writeToURL:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, error) as BOOL
}

fun NSArray.makeObjectsPerformSelector(aSelector: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("makeObjectsPerformSelector:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector)
}

fun NSArray.makeObjectsPerformSelector_withObject(aSelector: MemorySegment, argument: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("makeObjectsPerformSelector:withObject:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, argument)
}

/** @return NSArray<ObjectType> * */
fun NSArray.objectsAtIndexes(indexes: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("objectsAtIndexes:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, indexes) as MemorySegment
}

fun NSArray.objectAtIndexedSubscript(idx: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("objectAtIndexedSubscript:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, idx) as MemorySegment
}

fun NSArray.enumerateObjectsUsingBlock(block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateObjectsUsingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, block)
}

fun NSArray.enumerateObjectsWithOptions_usingBlock(opts: NSEnumerationOptions, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateObjectsWithOptions:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, opts, block)
}

fun NSArray.enumerateObjectsAtIndexes_options_usingBlock(s: MemorySegment, opts: NSEnumerationOptions, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateObjectsAtIndexes:options:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, s, opts, block)
}

fun NSArray.indexOfObjectPassingTest(predicate: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("indexOfObjectPassingTest:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, predicate) as NSUInteger
}

fun NSArray.indexOfObjectWithOptions_passingTest(opts: NSEnumerationOptions, predicate: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("indexOfObjectWithOptions:passingTest:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, opts, predicate) as NSUInteger
}

fun NSArray.indexOfObjectAtIndexes_options_passingTest(s: MemorySegment, opts: NSEnumerationOptions, predicate: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("indexOfObjectAtIndexes:options:passingTest:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, s, opts, predicate) as NSUInteger
}

fun NSArray.indexesOfObjectsPassingTest(predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("indexesOfObjectsPassingTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, predicate) as MemorySegment
}

fun NSArray.indexesOfObjectsWithOptions_passingTest(opts: NSEnumerationOptions, predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("indexesOfObjectsWithOptions:passingTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, opts, predicate) as MemorySegment
}

fun NSArray.indexesOfObjectsAtIndexes_options_passingTest(s: MemorySegment, opts: NSEnumerationOptions, predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("indexesOfObjectsAtIndexes:options:passingTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, s, opts, predicate) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.sortedArrayUsingComparator(cmptr: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayUsingComparator:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, cmptr) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.sortedArrayWithOptions_usingComparator(opts: NSSortOptions, cmptr: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayWithOptions:usingComparator:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, opts, cmptr) as MemorySegment
}

fun NSArray.indexOfObject_inSortedRange_options_usingComparator(obj: MemorySegment, r: NSRange, opts: NSBinarySearchingOptions, cmp: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("indexOfObject:inSortedRange:options:usingComparator:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, obj, r, opts, cmp) as NSUInteger
}

fun NSArray.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSArray.firstObject(): MemorySegment {
    val sel = ObjCRuntime.sel("firstObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSArray.lastObject(): MemorySegment {
    val sel = ObjCRuntime.sel("lastObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSArray.sortedArrayHint(): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayHint")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property description
fun NSArray.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property firstObject
fun NSArray.firstObject(): MemorySegment {
    val sel = ObjCRuntime.sel("firstObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property lastObject
fun NSArray.lastObject(): MemorySegment {
    val sel = ObjCRuntime.sel("lastObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property sortedArrayHint
fun NSArray.sortedArrayHint(): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayHint")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSArrayCreation on NSArray ─────────────────────────────────────────

fun NSArray.initWithObjects(firstObj: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithObjects:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, firstObj) as MemorySegment
}

fun NSArray.initWithArray(array: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithArray:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, array) as MemorySegment
}

fun NSArray.initWithArray_copyItems(array: MemorySegment, flag: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("initWithArray:copyItems:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, array, flag) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.initWithContentsOfURL_error(url: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, error) as MemorySegment
}

// Class method: +[NSArray array]
fun NSArray_array(): MemorySegment {
    val sel = ObjCRuntime.sel("array")
    val cls = ObjCRuntime.getClass("NSArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSArray arrayWithObject:]
fun NSArray_arrayWithObject(anObject: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithObject:")
    val cls = ObjCRuntime.getClass("NSArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, anObject) as MemorySegment
}

// Class method: +[NSArray arrayWithObjects:count:]
fun NSArray_arrayWithObjects_count(objects: MemorySegment, cnt: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithObjects:count:")
    val cls = ObjCRuntime.getClass("NSArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, objects, cnt) as MemorySegment
}

// Class method: +[NSArray arrayWithObjects:]
fun NSArray_arrayWithObjects(firstObj: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithObjects:")
    val cls = ObjCRuntime.getClass("NSArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, firstObj) as MemorySegment
}

// Class method: +[NSArray arrayWithArray:]
fun NSArray_arrayWithArray(array: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithArray:")
    val cls = ObjCRuntime.getClass("NSArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, array) as MemorySegment
}

// Class method: +[NSArray arrayWithContentsOfURL:error:]
fun NSArray_arrayWithContentsOfURL_error(url: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithContentsOfURL:error:")
    val cls = ObjCRuntime.getClass("NSArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url, error) as MemorySegment
}

// ── Category: NSArrayDiffing on NSArray ─────────────────────────────────────────

/** @return NSOrderedCollectionDifference<ObjectType> * */
fun NSArray.differenceFromArray_withOptions_usingEquivalenceTest(other: MemorySegment, options: NSOrderedCollectionDifferenceCalculationOptions, block: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("differenceFromArray:withOptions:usingEquivalenceTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, other, options, block) as MemorySegment
}

/** @return NSOrderedCollectionDifference<ObjectType> * */
fun NSArray.differenceFromArray_withOptions(other: MemorySegment, options: NSOrderedCollectionDifferenceCalculationOptions): MemorySegment {
    val sel = ObjCRuntime.sel("differenceFromArray:withOptions:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, other, options) as MemorySegment
}

/** @return NSOrderedCollectionDifference<ObjectType> * */
fun NSArray.differenceFromArray(other: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("differenceFromArray:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, other) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.arrayByApplyingDifference(difference: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayByApplyingDifference:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, difference) as MemorySegment
}

// ── Category: NSDeprecated on NSArray ─────────────────────────────────────────

fun NSArray.getObjects(objects: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getObjects:")
    ObjCRuntime.msgSend(null, ptr, sel, objects)
}

/** @return NSArray<ObjectType> * */
fun NSArray.initWithContentsOfFile(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfFile:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSArray.initWithContentsOfURL(url: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
}

fun NSArray.writeToFile_atomically(path: MemorySegment, useAuxiliaryFile: BOOL): BOOL {
    val sel = ObjCRuntime.sel("writeToFile:atomically:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, useAuxiliaryFile) as BOOL
}

fun NSArray.writeToURL_atomically(url: MemorySegment, atomically: BOOL): BOOL {
    val sel = ObjCRuntime.sel("writeToURL:atomically:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, atomically) as BOOL
}

// Class method: +[NSArray arrayWithContentsOfFile:]
fun NSArray_arrayWithContentsOfFile(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithContentsOfFile:")
    val cls = ObjCRuntime.getClass("NSArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, path) as MemorySegment
}

// Class method: +[NSArray arrayWithContentsOfURL:]
fun NSArray_arrayWithContentsOfURL(url: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("arrayWithContentsOfURL:")
    val cls = ObjCRuntime.getClass("NSArray")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url) as MemorySegment
}

// ── Category: NSArrayPathExtensions on NSArray ─────────────────────────────────────────

/** @return NSArray<NSString *> * */
fun NSArray.pathsMatchingExtensions(filterTypes: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("pathsMatchingExtensions:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, filterTypes) as MemorySegment
}

// ── Category: NSKeyValueCoding on NSArray ─────────────────────────────────────────

fun NSArray.valueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSArray.setValue_forKey(value: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setValue:forKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, key)
}

// ── Category: NSKeyValueObserverRegistration on NSArray ─────────────────────────────────────────

fun NSArray.addObserver_toObjectsAtIndexes_forKeyPath_options_context(observer: MemorySegment, indexes: MemorySegment, keyPath: MemorySegment, options: NSKeyValueObservingOptions, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addObserver:toObjectsAtIndexes:forKeyPath:options:context:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, indexes, keyPath, options, context)
}

fun NSArray.removeObserver_fromObjectsAtIndexes_forKeyPath_context(observer: MemorySegment, indexes: MemorySegment, keyPath: MemorySegment, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObserver:fromObjectsAtIndexes:forKeyPath:context:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, indexes, keyPath, context)
}

fun NSArray.removeObserver_fromObjectsAtIndexes_forKeyPath(observer: MemorySegment, indexes: MemorySegment, keyPath: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObserver:fromObjectsAtIndexes:forKeyPath:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, indexes, keyPath)
}

fun NSArray.addObserver_forKeyPath_options_context(observer: MemorySegment, keyPath: MemorySegment, options: NSKeyValueObservingOptions, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addObserver:forKeyPath:options:context:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath, options, context)
}

fun NSArray.removeObserver_forKeyPath_context(observer: MemorySegment, keyPath: MemorySegment, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObserver:forKeyPath:context:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath, context)
}

fun NSArray.removeObserver_forKeyPath(observer: MemorySegment, keyPath: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObserver:forKeyPath:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath)
}

// ── Category: NSSortDescriptorSorting on NSArray ─────────────────────────────────────────

/** @return NSArray<ObjectType> * */
fun NSArray.sortedArrayUsingDescriptors(sortDescriptors: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sortedArrayUsingDescriptors:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sortDescriptors) as MemorySegment
}

// ── Category: NSPredicateSupport on NSArray ─────────────────────────────────────────

/** @return NSArray<ObjectType> * */
fun NSArray.filteredArrayUsingPredicate(predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("filteredArrayUsingPredicate:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, predicate) as MemorySegment
}


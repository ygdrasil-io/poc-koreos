/**
 * Kotlin/JVM wrapper for Objective-C class: NSDictionary
 * Superclass: NSObject
 * Protocols: NSCopying, NSMutableCopying, NSSecureCoding, NSFastEnumeration
 */
open class NSDictionary(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDictionary") }
        
    }
    
    fun objectForKey(aKey: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aKey) as MemorySegment
    }
    
    /** @return NSEnumerator<KeyType> * */
    fun keyEnumerator(): MemorySegment {
        val sel = ObjCRuntime.sel("keyEnumerator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithObjects_forKeys_count(objects: MemorySegment, keys: MemorySegment, cnt: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObjects:forKeys:count:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, objects, keys, cnt) as MemorySegment
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

// ── Category: NSExtendedDictionary on NSDictionary ─────────────────────────────────────────

/** @return NSArray<KeyType> * */
fun NSDictionary.allKeysForObject(anObject: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("allKeysForObject:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, anObject) as MemorySegment
}

fun NSDictionary.descriptionWithLocale(locale: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionWithLocale:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale) as MemorySegment
}

fun NSDictionary.descriptionWithLocale_indent(locale: MemorySegment, level: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionWithLocale:indent:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, locale, level) as MemorySegment
}

fun NSDictionary.isEqualToDictionary(otherDictionary: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEqualToDictionary:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, otherDictionary) as BOOL
}

/** @return NSEnumerator<ObjectType> * */
fun NSDictionary.objectEnumerator(): MemorySegment {
    val sel = ObjCRuntime.sel("objectEnumerator")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSDictionary.objectsForKeys_notFoundMarker(keys: MemorySegment, marker: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("objectsForKeys:notFoundMarker:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keys, marker) as MemorySegment
}

fun NSDictionary.writeToURL_error(url: MemorySegment, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("writeToURL:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, error) as BOOL
}

/** @return NSArray<KeyType> * */
fun NSDictionary.keysSortedByValueUsingSelector(comparator: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("keysSortedByValueUsingSelector:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, comparator) as MemorySegment
}

fun NSDictionary.getObjects_andKeys_count(objects: MemorySegment, keys: MemorySegment, count: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("getObjects:andKeys:count:")
    ObjCRuntime.msgSend(null, ptr, sel, objects, keys, count)
}

fun NSDictionary.objectForKeyedSubscript(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("objectForKeyedSubscript:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSDictionary.enumerateKeysAndObjectsUsingBlock(block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateKeysAndObjectsUsingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, block)
}

fun NSDictionary.enumerateKeysAndObjectsWithOptions_usingBlock(opts: NSEnumerationOptions, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("enumerateKeysAndObjectsWithOptions:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, opts, block)
}

/** @return NSArray<KeyType> * */
fun NSDictionary.keysSortedByValueUsingComparator(cmptr: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("keysSortedByValueUsingComparator:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, cmptr) as MemorySegment
}

/** @return NSArray<KeyType> * */
fun NSDictionary.keysSortedByValueWithOptions_usingComparator(opts: NSSortOptions, cmptr: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("keysSortedByValueWithOptions:usingComparator:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, opts, cmptr) as MemorySegment
}

/** @return NSSet<KeyType> * */
fun NSDictionary.keysOfEntriesPassingTest(predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("keysOfEntriesPassingTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, predicate) as MemorySegment
}

/** @return NSSet<KeyType> * */
fun NSDictionary.keysOfEntriesWithOptions_passingTest(opts: NSEnumerationOptions, predicate: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("keysOfEntriesWithOptions:passingTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, opts, predicate) as MemorySegment
}

/** @return NSArray<KeyType> * */
fun NSDictionary.allKeys(): MemorySegment {
    val sel = ObjCRuntime.sel("allKeys")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<ObjectType> * */
fun NSDictionary.allValues(): MemorySegment {
    val sel = ObjCRuntime.sel("allValues")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDictionary.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDictionary.descriptionInStringsFileFormat(): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionInStringsFileFormat")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property allKeys
/** @return NSArray<KeyType> * */
fun NSDictionary.allKeys(): MemorySegment {
    val sel = ObjCRuntime.sel("allKeys")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property allValues
/** @return NSArray<ObjectType> * */
fun NSDictionary.allValues(): MemorySegment {
    val sel = ObjCRuntime.sel("allValues")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property description
fun NSDictionary.description(): MemorySegment {
    val sel = ObjCRuntime.sel("description")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property descriptionInStringsFileFormat
fun NSDictionary.descriptionInStringsFileFormat(): MemorySegment {
    val sel = ObjCRuntime.sel("descriptionInStringsFileFormat")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSDeprecated on NSDictionary ─────────────────────────────────────────

fun NSDictionary.getObjects_andKeys(objects: MemorySegment, keys: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getObjects:andKeys:")
    ObjCRuntime.msgSend(null, ptr, sel, objects, keys)
}

/** @return NSDictionary<KeyType,ObjectType> * */
fun NSDictionary.initWithContentsOfFile(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfFile:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path) as MemorySegment
}

/** @return NSDictionary<KeyType,ObjectType> * */
fun NSDictionary.initWithContentsOfURL(url: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
}

fun NSDictionary.writeToFile_atomically(path: MemorySegment, useAuxiliaryFile: BOOL): BOOL {
    val sel = ObjCRuntime.sel("writeToFile:atomically:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, path, useAuxiliaryFile) as BOOL
}

fun NSDictionary.writeToURL_atomically(url: MemorySegment, atomically: BOOL): BOOL {
    val sel = ObjCRuntime.sel("writeToURL:atomically:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, url, atomically) as BOOL
}

// Class method: +[NSDictionary dictionaryWithContentsOfFile:]
fun NSDictionary_dictionaryWithContentsOfFile(path: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithContentsOfFile:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, path) as MemorySegment
}

// Class method: +[NSDictionary dictionaryWithContentsOfURL:]
fun NSDictionary_dictionaryWithContentsOfURL(url: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithContentsOfURL:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url) as MemorySegment
}

// ── Category: NSDictionaryCreation on NSDictionary ─────────────────────────────────────────

fun NSDictionary.initWithObjectsAndKeys(firstObject: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithObjectsAndKeys:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, firstObject) as MemorySegment
}

fun NSDictionary.initWithDictionary(otherDictionary: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithDictionary:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherDictionary) as MemorySegment
}

fun NSDictionary.initWithDictionary_copyItems(otherDictionary: MemorySegment, flag: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("initWithDictionary:copyItems:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherDictionary, flag) as MemorySegment
}

fun NSDictionary.initWithObjects_forKeys(objects: MemorySegment, keys: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithObjects:forKeys:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, objects, keys) as MemorySegment
}

/** @return NSDictionary<NSString *,ObjectType> * */
fun NSDictionary.initWithContentsOfURL_error(url: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithContentsOfURL:error:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, error) as MemorySegment
}

// Class method: +[NSDictionary dictionary]
fun NSDictionary_dictionary(): MemorySegment {
    val sel = ObjCRuntime.sel("dictionary")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSDictionary dictionaryWithObject:forKey:]
fun NSDictionary_dictionaryWithObject_forKey(`object`: MemorySegment, key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithObject:forKey:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, `object`, key) as MemorySegment
}

// Class method: +[NSDictionary dictionaryWithObjects:forKeys:count:]
fun NSDictionary_dictionaryWithObjects_forKeys_count(objects: MemorySegment, keys: MemorySegment, cnt: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithObjects:forKeys:count:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, objects, keys, cnt) as MemorySegment
}

// Class method: +[NSDictionary dictionaryWithObjectsAndKeys:]
fun NSDictionary_dictionaryWithObjectsAndKeys(firstObject: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithObjectsAndKeys:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, firstObject) as MemorySegment
}

// Class method: +[NSDictionary dictionaryWithDictionary:]
fun NSDictionary_dictionaryWithDictionary(dict: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithDictionary:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, dict) as MemorySegment
}

// Class method: +[NSDictionary dictionaryWithObjects:forKeys:]
fun NSDictionary_dictionaryWithObjects_forKeys(objects: MemorySegment, keys: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithObjects:forKeys:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, objects, keys) as MemorySegment
}

// Class method: +[NSDictionary dictionaryWithContentsOfURL:error:]
fun NSDictionary_dictionaryWithContentsOfURL_error(url: MemorySegment, error: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithContentsOfURL:error:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url, error) as MemorySegment
}

// ── Category: NSSharedKeySetDictionary on NSDictionary ─────────────────────────────────────────

// Class method: +[NSDictionary sharedKeySetForKeys:]
fun NSDictionary_sharedKeySetForKeys(keys: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("sharedKeySetForKeys:")
    val cls = ObjCRuntime.getClass("NSDictionary")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, keys) as MemorySegment
}

// ── Category: NSGenericFastEnumeration on NSDictionary ─────────────────────────────────────────

fun NSDictionary.countByEnumeratingWithState_objects_count(state: MemorySegment, buffer: MemorySegment, len: NSUInteger): NSUInteger {
    val sel = ObjCRuntime.sel("countByEnumeratingWithState:objects:count:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, state, buffer, len) as NSUInteger
}

// ── Category: NSFileAttributes on NSDictionary ─────────────────────────────────────────

fun NSDictionary.fileSize(): Any {
    val sel = ObjCRuntime.sel("fileSize")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Any
}

fun NSDictionary.fileModificationDate(): MemorySegment {
    val sel = ObjCRuntime.sel("fileModificationDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDictionary.fileType(): MemorySegment {
    val sel = ObjCRuntime.sel("fileType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDictionary.filePosixPermissions(): NSUInteger {
    val sel = ObjCRuntime.sel("filePosixPermissions")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSDictionary.fileOwnerAccountName(): MemorySegment {
    val sel = ObjCRuntime.sel("fileOwnerAccountName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDictionary.fileGroupOwnerAccountName(): MemorySegment {
    val sel = ObjCRuntime.sel("fileGroupOwnerAccountName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDictionary.fileSystemNumber(): NSInteger {
    val sel = ObjCRuntime.sel("fileSystemNumber")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSDictionary.fileSystemFileNumber(): NSUInteger {
    val sel = ObjCRuntime.sel("fileSystemFileNumber")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSDictionary.fileExtensionHidden(): BOOL {
    val sel = ObjCRuntime.sel("fileExtensionHidden")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSDictionary.fileHFSCreatorCode(): OSType {
    val sel = ObjCRuntime.sel("fileHFSCreatorCode")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as OSType
}

fun NSDictionary.fileHFSTypeCode(): OSType {
    val sel = ObjCRuntime.sel("fileHFSTypeCode")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as OSType
}

fun NSDictionary.fileIsImmutable(): BOOL {
    val sel = ObjCRuntime.sel("fileIsImmutable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSDictionary.fileIsAppendOnly(): BOOL {
    val sel = ObjCRuntime.sel("fileIsAppendOnly")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSDictionary.fileCreationDate(): MemorySegment {
    val sel = ObjCRuntime.sel("fileCreationDate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDictionary.fileOwnerAccountID(): MemorySegment {
    val sel = ObjCRuntime.sel("fileOwnerAccountID")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSDictionary.fileGroupOwnerAccountID(): MemorySegment {
    val sel = ObjCRuntime.sel("fileGroupOwnerAccountID")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSKeyValueCoding on NSDictionary ─────────────────────────────────────────

fun NSDictionary.valueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}


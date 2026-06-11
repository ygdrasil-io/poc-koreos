/**
 * Kotlin/JVM wrapper for Objective-C class: NSMetadataQuery
 * Superclass: NSObject
 */
open class NSMetadataQuery(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMetadataQuery") }
        
    }
    
    fun startQuery(): BOOL {
        val sel = ObjCRuntime.sel("startQuery")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun stopQuery(): Unit {
        val sel = ObjCRuntime.sel("stopQuery")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun disableUpdates(): Unit {
        val sel = ObjCRuntime.sel("disableUpdates")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun enableUpdates(): Unit {
        val sel = ObjCRuntime.sel("enableUpdates")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resultAtIndex(idx: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("resultAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, idx) as MemorySegment
    }
    
    fun enumerateResultsUsingBlock(block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateResultsUsingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, block)
    }
    
    fun enumerateResultsWithOptions_usingBlock(opts: NSEnumerationOptions, block: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enumerateResultsWithOptions:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, opts, block)
    }
    
    fun indexOfResult(result: MemorySegment): NSUInteger {
        val sel = ObjCRuntime.sel("indexOfResult:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, result) as NSUInteger
    }
    
    fun valueOfAttribute_forResultAtIndex(attrName: MemorySegment, idx: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("valueOfAttribute:forResultAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attrName, idx) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun valueOfAttribute_forResultAtIndex(attrName: String, idx: NSUInteger): MemorySegment = valueOfAttribute_forResultAtIndex(ObjCRuntime.newNSString(Arena.global(), attrName), idx)
    
    // @property delegate
    /** @return id<NSMetadataQueryDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property predicate
    fun predicate(): MemorySegment {
        val sel = ObjCRuntime.sel("predicate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPredicate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPredicate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property valueListAttributes
    /** @return NSArray<NSString *> * */
    fun valueListAttributes(): MemorySegment {
        val sel = ObjCRuntime.sel("valueListAttributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setValueListAttributes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setValueListAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property groupingAttributes
    /** @return NSArray<NSString *> * */
    fun groupingAttributes(): MemorySegment {
        val sel = ObjCRuntime.sel("groupingAttributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setGroupingAttributes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setGroupingAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property notificationBatchingInterval
    fun notificationBatchingInterval(): NSTimeInterval {
        val sel = ObjCRuntime.sel("notificationBatchingInterval")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setNotificationBatchingInterval(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setNotificationBatchingInterval:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property searchScopes
    fun searchScopes(): MemorySegment {
        val sel = ObjCRuntime.sel("searchScopes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSearchScopes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSearchScopes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property searchItems
    fun searchItems(): MemorySegment {
        val sel = ObjCRuntime.sel("searchItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSearchItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSearchItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property operationQueue
    fun operationQueue(): MemorySegment {
        val sel = ObjCRuntime.sel("operationQueue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setOperationQueue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setOperationQueue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property started
    fun isStarted(): BOOL {
        val sel = ObjCRuntime.sel("isStarted")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property gathering
    fun isGathering(): BOOL {
        val sel = ObjCRuntime.sel("isGathering")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property stopped
    fun isStopped(): BOOL {
        val sel = ObjCRuntime.sel("isStopped")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property resultCount
    fun resultCount(): NSUInteger {
        val sel = ObjCRuntime.sel("resultCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property results
    fun results(): MemorySegment {
        val sel = ObjCRuntime.sel("results")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property valueLists
    /** @return NSDictionary<NSString *,NSArray<NSMetadataQueryAttributeValueTuple *> *> * */
    fun valueLists(): MemorySegment {
        val sel = ObjCRuntime.sel("valueLists")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property groupedResults
    /** @return NSArray<NSMetadataQueryResultGroup *> * */
    fun groupedResults(): MemorySegment {
        val sel = ObjCRuntime.sel("groupedResults")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _flags: NSUInteger
    // ivar: _interval: NSTimeInterval
    // ivar: _private: MemorySegment
    // ivar: _reserved: MemorySegment
}


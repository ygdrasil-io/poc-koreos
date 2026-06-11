/**
 * Kotlin/JVM wrapper for Objective-C class: NSUserActivity
 * Superclass: NSObject
 */
open class NSUserActivity(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUserActivity") }
        
        fun deleteSavedUserActivitiesWithPersistentIdentifiers_completionHandler(persistentIdentifiers: MemorySegment, handler: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("deleteSavedUserActivitiesWithPersistentIdentifiers:completionHandler:")
            ObjCRuntime.msgSend(null, _class, sel, persistentIdentifiers, handler)
        }
        
        fun deleteAllSavedUserActivitiesWithCompletionHandler(handler: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("deleteAllSavedUserActivitiesWithCompletionHandler:")
            ObjCRuntime.msgSend(null, _class, sel, handler)
        }
        
    }
    
    fun initWithActivityType(activityType: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithActivityType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, activityType) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithActivityType(activityType: String): MemorySegment = initWithActivityType(ObjCRuntime.newNSString(Arena.global(), activityType))
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun addUserInfoEntriesFromDictionary(otherDictionary: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addUserInfoEntriesFromDictionary:")
        ObjCRuntime.msgSend(null, ptr, sel, otherDictionary)
    }
    
    fun becomeCurrent(): Unit {
        val sel = ObjCRuntime.sel("becomeCurrent")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resignCurrent(): Unit {
        val sel = ObjCRuntime.sel("resignCurrent")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun invalidate(): Unit {
        val sel = ObjCRuntime.sel("invalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun getContinuationStreamsWithCompletionHandler(completionHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getContinuationStreamsWithCompletionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, completionHandler)
    }
    
    // @property activityType
    fun activityType(): MemorySegment {
        val sel = ObjCRuntime.sel("activityType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun activityTypeAsString(): String = ObjCRuntime.toJavaString(activityType())
    
    // @property title
    fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property userInfo
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setUserInfo(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setUserInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property requiredUserInfoKeys
    /** @return NSSet<NSString *> * */
    fun requiredUserInfoKeys(): MemorySegment {
        val sel = ObjCRuntime.sel("requiredUserInfoKeys")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRequiredUserInfoKeys(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRequiredUserInfoKeys:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property needsSave
    fun needsSave(): BOOL {
        val sel = ObjCRuntime.sel("needsSave")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setNeedsSave(value: BOOL) {
        val sel = ObjCRuntime.sel("setNeedsSave:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property webpageURL
    fun webpageURL(): MemorySegment {
        val sel = ObjCRuntime.sel("webpageURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setWebpageURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setWebpageURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property referrerURL
    fun referrerURL(): MemorySegment {
        val sel = ObjCRuntime.sel("referrerURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setReferrerURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setReferrerURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property expirationDate
    fun expirationDate(): MemorySegment {
        val sel = ObjCRuntime.sel("expirationDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setExpirationDate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setExpirationDate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property keywords
    /** @return NSSet<NSString *> * */
    fun keywords(): MemorySegment {
        val sel = ObjCRuntime.sel("keywords")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setKeywords(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setKeywords:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property supportsContinuationStreams
    fun supportsContinuationStreams(): BOOL {
        val sel = ObjCRuntime.sel("supportsContinuationStreams")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSupportsContinuationStreams(value: BOOL) {
        val sel = ObjCRuntime.sel("setSupportsContinuationStreams:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSUserActivityDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property targetContentIdentifier
    fun targetContentIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("targetContentIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTargetContentIdentifier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTargetContentIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun targetContentIdentifierAsString(): String = ObjCRuntime.toJavaString(targetContentIdentifier())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setTargetContentIdentifier(value: String) = setTargetContentIdentifier(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property eligibleForHandoff
    fun isEligibleForHandoff(): BOOL {
        val sel = ObjCRuntime.sel("isEligibleForHandoff")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEligibleForHandoff(value: BOOL) {
        val sel = ObjCRuntime.sel("setEligibleForHandoff:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property eligibleForSearch
    fun isEligibleForSearch(): BOOL {
        val sel = ObjCRuntime.sel("isEligibleForSearch")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEligibleForSearch(value: BOOL) {
        val sel = ObjCRuntime.sel("setEligibleForSearch:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property eligibleForPublicIndexing
    fun isEligibleForPublicIndexing(): BOOL {
        val sel = ObjCRuntime.sel("isEligibleForPublicIndexing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEligibleForPublicIndexing(value: BOOL) {
        val sel = ObjCRuntime.sel("setEligibleForPublicIndexing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property eligibleForPrediction
    fun isEligibleForPrediction(): BOOL {
        val sel = ObjCRuntime.sel("isEligibleForPrediction")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEligibleForPrediction(value: BOOL) {
        val sel = ObjCRuntime.sel("setEligibleForPrediction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property persistentIdentifier
    fun persistentIdentifier(): NSUserActivityPersistentIdentifier {
        val sel = ObjCRuntime.sel("persistentIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserActivityPersistentIdentifier
    }
    fun setPersistentIdentifier(value: NSUserActivityPersistentIdentifier) {
        val sel = ObjCRuntime.sel("setPersistentIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


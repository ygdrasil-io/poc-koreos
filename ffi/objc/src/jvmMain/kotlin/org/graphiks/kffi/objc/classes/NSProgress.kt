/**
 * Kotlin/JVM wrapper for Objective-C class: NSProgress
 * Superclass: NSObject
 */
open class NSProgress(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSProgress") }
        
        fun currentProgress(): MemorySegment {
            val sel = ObjCRuntime.sel("currentProgress")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun progressWithTotalUnitCount(unitCount: int64_t): MemorySegment {
            val sel = ObjCRuntime.sel("progressWithTotalUnitCount:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, unitCount) as MemorySegment
        }
        
        fun discreteProgressWithTotalUnitCount(unitCount: int64_t): MemorySegment {
            val sel = ObjCRuntime.sel("discreteProgressWithTotalUnitCount:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, unitCount) as MemorySegment
        }
        
        fun progressWithTotalUnitCount_parent_pendingUnitCount(unitCount: int64_t, parent: MemorySegment, portionOfParentTotalUnitCount: int64_t): MemorySegment {
            val sel = ObjCRuntime.sel("progressWithTotalUnitCount:parent:pendingUnitCount:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, unitCount, parent, portionOfParentTotalUnitCount) as MemorySegment
        }
        
        fun addSubscriberForFileURL_withPublishingHandler(url: MemorySegment, publishingHandler: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("addSubscriberForFileURL:withPublishingHandler:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url, publishingHandler) as MemorySegment
        }
        
        fun removeSubscriber(subscriber: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("removeSubscriber:")
            ObjCRuntime.msgSend(null, _class, sel, subscriber)
        }
        
    }
    
    fun initWithParent_userInfo(parentProgressOrNil: MemorySegment, userInfoOrNil: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithParent:userInfo:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, parentProgressOrNil, userInfoOrNil) as MemorySegment
    }
    
    fun becomeCurrentWithPendingUnitCount(unitCount: int64_t): Unit {
        val sel = ObjCRuntime.sel("becomeCurrentWithPendingUnitCount:")
        ObjCRuntime.msgSend(null, ptr, sel, unitCount)
    }
    
    fun performAsCurrentWithPendingUnitCount_usingBlock(unitCount: int64_t, work: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performAsCurrentWithPendingUnitCount:usingBlock:")
        ObjCRuntime.msgSend(null, ptr, sel, unitCount, work)
    }
    
    fun resignCurrent(): Unit {
        val sel = ObjCRuntime.sel("resignCurrent")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addChild_withPendingUnitCount(child: MemorySegment, inUnitCount: int64_t): Unit {
        val sel = ObjCRuntime.sel("addChild:withPendingUnitCount:")
        ObjCRuntime.msgSend(null, ptr, sel, child, inUnitCount)
    }
    
    fun setUserInfoObject_forKey(objectOrNil: MemorySegment, key: NSProgressUserInfoKey): Unit {
        val sel = ObjCRuntime.sel("setUserInfoObject:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, objectOrNil, key)
    }
    
    fun cancel(): Unit {
        val sel = ObjCRuntime.sel("cancel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun pause(): Unit {
        val sel = ObjCRuntime.sel("pause")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun resume(): Unit {
        val sel = ObjCRuntime.sel("resume")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun publish(): Unit {
        val sel = ObjCRuntime.sel("publish")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun unpublish(): Unit {
        val sel = ObjCRuntime.sel("unpublish")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property totalUnitCount
    fun totalUnitCount(): int64_t {
        val sel = ObjCRuntime.sel("totalUnitCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    fun setTotalUnitCount(value: int64_t) {
        val sel = ObjCRuntime.sel("setTotalUnitCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property completedUnitCount
    fun completedUnitCount(): int64_t {
        val sel = ObjCRuntime.sel("completedUnitCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as int64_t
    }
    fun setCompletedUnitCount(value: int64_t) {
        val sel = ObjCRuntime.sel("setCompletedUnitCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property localizedDescription
    fun localizedDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLocalizedDescription(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocalizedDescription:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedDescriptionAsString(): String = ObjCRuntime.toJavaString(localizedDescription())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setLocalizedDescription(value: String) = setLocalizedDescription(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property localizedAdditionalDescription
    fun localizedAdditionalDescription(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedAdditionalDescription")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLocalizedAdditionalDescription(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLocalizedAdditionalDescription:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedAdditionalDescriptionAsString(): String = ObjCRuntime.toJavaString(localizedAdditionalDescription())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setLocalizedAdditionalDescription(value: String) = setLocalizedAdditionalDescription(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property cancellable
    fun isCancellable(): BOOL {
        val sel = ObjCRuntime.sel("isCancellable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCancellable(value: BOOL) {
        val sel = ObjCRuntime.sel("setCancellable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pausable
    fun isPausable(): BOOL {
        val sel = ObjCRuntime.sel("isPausable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPausable(value: BOOL) {
        val sel = ObjCRuntime.sel("setPausable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cancelled
    fun isCancelled(): BOOL {
        val sel = ObjCRuntime.sel("isCancelled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property paused
    fun isPaused(): BOOL {
        val sel = ObjCRuntime.sel("isPaused")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property cancellationHandler
    fun cancellationHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("cancellationHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCancellationHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCancellationHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pausingHandler
    fun pausingHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("pausingHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPausingHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPausingHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property resumingHandler
    fun resumingHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("resumingHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setResumingHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setResumingHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property indeterminate
    fun isIndeterminate(): BOOL {
        val sel = ObjCRuntime.sel("isIndeterminate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property fractionCompleted
    fun fractionCompleted(): Double {
        val sel = ObjCRuntime.sel("fractionCompleted")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    
    // @property finished
    fun isFinished(): BOOL {
        val sel = ObjCRuntime.sel("isFinished")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property userInfo
    /** @return NSDictionary<NSProgressUserInfoKey,id> * */
    fun userInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("userInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property kind
    fun kind(): NSProgressKind {
        val sel = ObjCRuntime.sel("kind")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSProgressKind
    }
    fun setKind(value: NSProgressKind) {
        val sel = ObjCRuntime.sel("setKind:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property estimatedTimeRemaining
    fun estimatedTimeRemaining(): MemorySegment {
        val sel = ObjCRuntime.sel("estimatedTimeRemaining")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setEstimatedTimeRemaining(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEstimatedTimeRemaining:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property throughput
    fun throughput(): MemorySegment {
        val sel = ObjCRuntime.sel("throughput")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setThroughput(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setThroughput:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fileOperationKind
    fun fileOperationKind(): NSProgressFileOperationKind {
        val sel = ObjCRuntime.sel("fileOperationKind")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSProgressFileOperationKind
    }
    fun setFileOperationKind(value: NSProgressFileOperationKind) {
        val sel = ObjCRuntime.sel("setFileOperationKind:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fileURL
    fun fileURL(): MemorySegment {
        val sel = ObjCRuntime.sel("fileURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFileURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFileURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fileTotalCount
    fun fileTotalCount(): MemorySegment {
        val sel = ObjCRuntime.sel("fileTotalCount")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFileTotalCount(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFileTotalCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fileCompletedCount
    fun fileCompletedCount(): MemorySegment {
        val sel = ObjCRuntime.sel("fileCompletedCount")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFileCompletedCount(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFileCompletedCount:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property old
    fun isOld(): BOOL {
        val sel = ObjCRuntime.sel("isOld")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}


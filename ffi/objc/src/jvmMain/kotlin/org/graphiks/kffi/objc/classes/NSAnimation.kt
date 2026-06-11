/**
 * Kotlin/JVM wrapper for Objective-C class: NSAnimation
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSAnimation(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAnimation") }
        
    }
    
    fun initWithDuration_animationCurve(duration: NSTimeInterval, animationCurve: NSAnimationCurve): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDuration:animationCurve:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, duration, animationCurve) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun startAnimation(): Unit {
        val sel = ObjCRuntime.sel("startAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun stopAnimation(): Unit {
        val sel = ObjCRuntime.sel("stopAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addProgressMark(progressMark: NSAnimationProgress): Unit {
        val sel = ObjCRuntime.sel("addProgressMark:")
        ObjCRuntime.msgSend(null, ptr, sel, progressMark)
    }
    
    fun removeProgressMark(progressMark: NSAnimationProgress): Unit {
        val sel = ObjCRuntime.sel("removeProgressMark:")
        ObjCRuntime.msgSend(null, ptr, sel, progressMark)
    }
    
    fun startWhenAnimation_reachesProgress(animation: MemorySegment, startProgress: NSAnimationProgress): Unit {
        val sel = ObjCRuntime.sel("startWhenAnimation:reachesProgress:")
        ObjCRuntime.msgSend(null, ptr, sel, animation, startProgress)
    }
    
    fun stopWhenAnimation_reachesProgress(animation: MemorySegment, stopProgress: NSAnimationProgress): Unit {
        val sel = ObjCRuntime.sel("stopWhenAnimation:reachesProgress:")
        ObjCRuntime.msgSend(null, ptr, sel, animation, stopProgress)
    }
    
    fun clearStartAnimation(): Unit {
        val sel = ObjCRuntime.sel("clearStartAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun clearStopAnimation(): Unit {
        val sel = ObjCRuntime.sel("clearStopAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property animating
    fun isAnimating(): BOOL {
        val sel = ObjCRuntime.sel("isAnimating")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property currentProgress
    fun currentProgress(): NSAnimationProgress {
        val sel = ObjCRuntime.sel("currentProgress")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as NSAnimationProgress
    }
    fun setCurrentProgress(value: NSAnimationProgress) {
        val sel = ObjCRuntime.sel("setCurrentProgress:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property duration
    fun duration(): NSTimeInterval {
        val sel = ObjCRuntime.sel("duration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setDuration(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setDuration:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property animationBlockingMode
    fun animationBlockingMode(): NSAnimationBlockingMode {
        val sel = ObjCRuntime.sel("animationBlockingMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAnimationBlockingMode
    }
    fun setAnimationBlockingMode(value: NSAnimationBlockingMode) {
        val sel = ObjCRuntime.sel("setAnimationBlockingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property frameRate
    fun frameRate(): Float {
        val sel = ObjCRuntime.sel("frameRate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    fun setFrameRate(value: Float) {
        val sel = ObjCRuntime.sel("setFrameRate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property animationCurve
    fun animationCurve(): NSAnimationCurve {
        val sel = ObjCRuntime.sel("animationCurve")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAnimationCurve
    }
    fun setAnimationCurve(value: NSAnimationCurve) {
        val sel = ObjCRuntime.sel("setAnimationCurve:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentValue
    fun currentValue(): Float {
        val sel = ObjCRuntime.sel("currentValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    
    // @property delegate
    /** @return id<NSAnimationDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property progressMarks
    /** @return NSArray<NSNumber *> * */
    fun progressMarks(): MemorySegment {
        val sel = ObjCRuntime.sel("progressMarks")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setProgressMarks(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setProgressMarks:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property runLoopModesForAnimating
    /** @return NSArray<NSRunLoopMode> * */
    fun runLoopModesForAnimating(): MemorySegment {
        val sel = ObjCRuntime.sel("runLoopModesForAnimating")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


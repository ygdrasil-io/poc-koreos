package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSAnimation
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSAnimation(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAnimation") }
        
    }
    
    open fun initWithDuration_animationCurve(duration: NSTimeInterval, animationCurve: NSAnimationCurve): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDuration:animationCurve:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, duration, animationCurve) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun startAnimation(): Unit {
        val sel = ObjCRuntime.sel("startAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun stopAnimation(): Unit {
        val sel = ObjCRuntime.sel("stopAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun addProgressMark(progressMark: NSAnimationProgress): Unit {
        val sel = ObjCRuntime.sel("addProgressMark:")
        ObjCRuntime.msgSend(null, ptr, sel, progressMark)
    }
    
    open fun removeProgressMark(progressMark: NSAnimationProgress): Unit {
        val sel = ObjCRuntime.sel("removeProgressMark:")
        ObjCRuntime.msgSend(null, ptr, sel, progressMark)
    }
    
    open fun startWhenAnimation_reachesProgress(animation: MemorySegment, startProgress: NSAnimationProgress): Unit {
        val sel = ObjCRuntime.sel("startWhenAnimation:reachesProgress:")
        ObjCRuntime.msgSend(null, ptr, sel, animation, startProgress)
    }
    
    open fun stopWhenAnimation_reachesProgress(animation: MemorySegment, stopProgress: NSAnimationProgress): Unit {
        val sel = ObjCRuntime.sel("stopWhenAnimation:reachesProgress:")
        ObjCRuntime.msgSend(null, ptr, sel, animation, stopProgress)
    }
    
    open fun clearStartAnimation(): Unit {
        val sel = ObjCRuntime.sel("clearStartAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun clearStopAnimation(): Unit {
        val sel = ObjCRuntime.sel("clearStopAnimation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property animating
    open fun isAnimating(): BOOL {
        val sel = ObjCRuntime.sel("isAnimating")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property currentProgress
    open fun currentProgress(): NSAnimationProgress {
        val sel = ObjCRuntime.sel("currentProgress")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as NSAnimationProgress
    }
    open fun setCurrentProgress(value: NSAnimationProgress) {
        val sel = ObjCRuntime.sel("setCurrentProgress:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property duration
    open fun duration(): NSTimeInterval {
        val sel = ObjCRuntime.sel("duration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    open fun setDuration(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setDuration:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property animationBlockingMode
    open fun animationBlockingMode(): NSAnimationBlockingMode {
        val sel = ObjCRuntime.sel("animationBlockingMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAnimationBlockingMode
    }
    open fun setAnimationBlockingMode(value: NSAnimationBlockingMode) {
        val sel = ObjCRuntime.sel("setAnimationBlockingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property frameRate
    open fun frameRate(): Float {
        val sel = ObjCRuntime.sel("frameRate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    open fun setFrameRate(value: Float) {
        val sel = ObjCRuntime.sel("setFrameRate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property animationCurve
    open fun animationCurve(): NSAnimationCurve {
        val sel = ObjCRuntime.sel("animationCurve")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAnimationCurve
    }
    open fun setAnimationCurve(value: NSAnimationCurve) {
        val sel = ObjCRuntime.sel("setAnimationCurve:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentValue
    open fun currentValue(): Float {
        val sel = ObjCRuntime.sel("currentValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    
    // @property delegate
    /** @return id<NSAnimationDelegate> */
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property progressMarks
    /** @return NSArray<NSNumber *> * */
    open fun progressMarks(): MemorySegment {
        val sel = ObjCRuntime.sel("progressMarks")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setProgressMarks(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setProgressMarks:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property runLoopModesForAnimating
    /** @return NSArray<NSRunLoopMode> * */
    open fun runLoopModesForAnimating(): MemorySegment {
        val sel = ObjCRuntime.sel("runLoopModesForAnimating")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


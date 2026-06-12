package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: CAMediaTiming
 */
interface CAMediaTiming {
    fun beginTime(): CFTimeInterval
    
    fun setBeginTime(beginTime: CFTimeInterval)
    
    fun duration(): CFTimeInterval
    
    fun setDuration(duration: CFTimeInterval)
    
    fun speed(): Float
    
    fun setSpeed(speed: Float)
    
    fun timeOffset(): CFTimeInterval
    
    fun setTimeOffset(timeOffset: CFTimeInterval)
    
    fun repeatCount(): Float
    
    fun setRepeatCount(repeatCount: Float)
    
    fun repeatDuration(): CFTimeInterval
    
    fun setRepeatDuration(repeatDuration: CFTimeInterval)
    
    fun autoreverses(): BOOL
    
    fun setAutoreverses(autoreverses: BOOL)
    
    fun fillMode(): CAMediaTimingFillMode
    
    fun setFillMode(fillMode: CAMediaTimingFillMode)
    
    // @property beginTime
}


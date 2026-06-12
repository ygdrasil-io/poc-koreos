package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSAnimatablePropertyContainer
 */
interface NSAnimatablePropertyContainer {
    fun animator(): MemorySegment
    
    fun animationForKey(key: NSAnimatablePropertyKey): MemorySegment
    
    fun defaultAnimationForKey(key: NSAnimatablePropertyKey): MemorySegment
    
    /** @return NSDictionary<NSAnimatablePropertyKey,id> * */
    fun animations(): MemorySegment
    
    fun setAnimations(animations: MemorySegment)
    
    // @property animations
    /** @return NSDictionary<NSAnimatablePropertyKey,id> * */
}


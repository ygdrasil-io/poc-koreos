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
    fun animations(): MemorySegment
    fun setAnimations(value: MemorySegment)
    
}


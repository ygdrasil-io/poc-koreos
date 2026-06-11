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
    fun beginTime(): CFTimeInterval
    fun setBeginTime(value: CFTimeInterval)
    
    // @property duration
    fun duration(): CFTimeInterval
    fun setDuration(value: CFTimeInterval)
    
    // @property speed
    fun speed(): Float
    fun setSpeed(value: Float)
    
    // @property timeOffset
    fun timeOffset(): CFTimeInterval
    fun setTimeOffset(value: CFTimeInterval)
    
    // @property repeatCount
    fun repeatCount(): Float
    fun setRepeatCount(value: Float)
    
    // @property repeatDuration
    fun repeatDuration(): CFTimeInterval
    fun setRepeatDuration(value: CFTimeInterval)
    
    // @property autoreverses
    fun autoreverses(): BOOL
    fun setAutoreverses(value: BOOL)
    
    // @property fillMode
    fun fillMode(): CAMediaTimingFillMode
    fun setFillMode(value: CAMediaTimingFillMode)
    
}


/**
 * Kotlin/JVM interface for Objective-C protocol: NSDiscardableContent
 */
interface NSDiscardableContent {
    fun beginContentAccess(): BOOL
    
    fun endContentAccess()
    
    fun discardContentIfPossible()
    
    fun isContentDiscarded(): BOOL
    
}


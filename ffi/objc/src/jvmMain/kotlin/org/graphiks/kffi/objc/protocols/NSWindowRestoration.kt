/**
 * Kotlin/JVM interface for Objective-C protocol: NSWindowRestoration
 * Inherits protocols: NSObject
 */
interface NSWindowRestoration : NSObject {
    fun restoreWindowWithIdentifier_state_completionHandler(identifier: NSUserInterfaceItemIdentifier, state: MemorySegment, completionHandler: MemorySegment)
    
}


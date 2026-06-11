/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextFinderBarContainer
 * Inherits protocols: NSObject
 */
interface NSTextFinderBarContainer : NSObject {
    fun findBarViewDidChangeHeight()
    
    // @optional
    fun contentView(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'contentView' not implemented")
    
    fun findBarView(): MemorySegment
    
    fun setFindBarView(findBarView: MemorySegment)
    
    fun isFindBarVisible(): BOOL
    
    fun setFindBarVisible(findBarVisible: BOOL)
    
    // @property findBarView
    fun findBarView(): MemorySegment
    fun setFindBarView(value: MemorySegment)
    
    // @property findBarVisible
    fun isFindBarVisible(): BOOL
    fun setFindBarVisible(value: BOOL)
    
}


/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewFlowLayoutInvalidationContext
 * Superclass: NSCollectionViewLayoutInvalidationContext
 */
open class NSCollectionViewFlowLayoutInvalidationContext(ptr: MemorySegment) : NSCollectionViewLayoutInvalidationContext(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewFlowLayoutInvalidationContext") }
        
    }
    
    // @property invalidateFlowLayoutDelegateMetrics
    fun invalidateFlowLayoutDelegateMetrics(): BOOL {
        val sel = ObjCRuntime.sel("invalidateFlowLayoutDelegateMetrics")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setInvalidateFlowLayoutDelegateMetrics(value: BOOL) {
        val sel = ObjCRuntime.sel("setInvalidateFlowLayoutDelegateMetrics:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property invalidateFlowLayoutAttributes
    fun invalidateFlowLayoutAttributes(): BOOL {
        val sel = ObjCRuntime.sel("invalidateFlowLayoutAttributes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setInvalidateFlowLayoutAttributes(value: BOOL) {
        val sel = ObjCRuntime.sel("setInvalidateFlowLayoutAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


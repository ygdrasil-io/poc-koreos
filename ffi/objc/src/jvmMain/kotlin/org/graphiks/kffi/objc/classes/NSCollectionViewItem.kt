/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewItem
 * Superclass: NSViewController
 * Protocols: NSCopying, NSCollectionViewElement
 */
open class NSCollectionViewItem(ptr: MemorySegment) : NSViewController(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewItem") }
        
    }
    
    // @property collectionView
    fun collectionView(): MemorySegment {
        val sel = ObjCRuntime.sel("collectionView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selected
    fun isSelected(): BOOL {
        val sel = ObjCRuntime.sel("isSelected")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelected(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelected:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property highlightState
    fun highlightState(): NSCollectionViewItemHighlightState {
        val sel = ObjCRuntime.sel("highlightState")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCollectionViewItemHighlightState
    }
    fun setHighlightState(value: NSCollectionViewItemHighlightState) {
        val sel = ObjCRuntime.sel("setHighlightState:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageView
    fun imageView(): MemorySegment {
        val sel = ObjCRuntime.sel("imageView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImageView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImageView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textField
    fun textField(): MemorySegment {
        val sel = ObjCRuntime.sel("textField")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextField(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextField:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property draggingImageComponents
    /** @return NSArray<NSDraggingImageComponent *> * */
    fun draggingImageComponents(): MemorySegment {
        val sel = ObjCRuntime.sel("draggingImageComponents")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


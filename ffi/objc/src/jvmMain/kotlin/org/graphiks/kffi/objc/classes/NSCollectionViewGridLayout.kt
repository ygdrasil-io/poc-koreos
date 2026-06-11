/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionViewGridLayout
 * Superclass: NSCollectionViewLayout
 */
open class NSCollectionViewGridLayout(ptr: MemorySegment) : NSCollectionViewLayout(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionViewGridLayout") }
        
    }
    
    // @property margins
    fun margins(): NSEdgeInsets {
        val sel = ObjCRuntime.sel("margins")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets"), ptr, sel) as NSEdgeInsets
    }
    fun setMargins(value: NSEdgeInsets) {
        val sel = ObjCRuntime.sel("setMargins:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("top"), ValueLayout.JAVA_DOUBLE.withName("left"), ValueLayout.JAVA_DOUBLE.withName("bottom"), ValueLayout.JAVA_DOUBLE.withName("right")).withName("NSEdgeInsets")))
    }
    
    // @property minimumInteritemSpacing
    fun minimumInteritemSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("minimumInteritemSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumInteritemSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumInteritemSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumLineSpacing
    fun minimumLineSpacing(): CGFloat {
        val sel = ObjCRuntime.sel("minimumLineSpacing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setMinimumLineSpacing(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinimumLineSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumNumberOfRows
    fun maximumNumberOfRows(): NSUInteger {
        val sel = ObjCRuntime.sel("maximumNumberOfRows")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setMaximumNumberOfRows(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMaximumNumberOfRows:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumNumberOfColumns
    fun maximumNumberOfColumns(): NSUInteger {
        val sel = ObjCRuntime.sel("maximumNumberOfColumns")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setMaximumNumberOfColumns(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setMaximumNumberOfColumns:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minimumItemSize
    fun minimumItemSize(): NSSize {
        val sel = ObjCRuntime.sel("minimumItemSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMinimumItemSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMinimumItemSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property maximumItemSize
    fun maximumItemSize(): NSSize {
        val sel = ObjCRuntime.sel("maximumItemSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setMaximumItemSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setMaximumItemSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property backgroundColors
    /** @return NSArray<NSColor *> * */
    fun backgroundColors(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColors(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColors:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


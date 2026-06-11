/**
 * Kotlin/JVM wrapper for Objective-C class: NSForm
 * Superclass: NSMatrix
 */
open class NSForm(ptr: MemorySegment) : NSMatrix(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSForm") }
        
    }
    
    fun indexOfSelectedItem(): NSInteger {
        val sel = ObjCRuntime.sel("indexOfSelectedItem")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    fun setEntryWidth(width: CGFloat): Unit {
        val sel = ObjCRuntime.sel("setEntryWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, width)
    }
    
    fun setInterlineSpacing(spacing: CGFloat): Unit {
        val sel = ObjCRuntime.sel("setInterlineSpacing:")
        ObjCRuntime.msgSend(null, ptr, sel, spacing)
    }
    
    fun setBordered(flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setBordered:")
        ObjCRuntime.msgSend(null, ptr, sel, flag)
    }
    
    fun setBezeled(flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setBezeled:")
        ObjCRuntime.msgSend(null, ptr, sel, flag)
    }
    
    fun setTitleAlignment(mode: NSTextAlignment): Unit {
        val sel = ObjCRuntime.sel("setTitleAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, mode)
    }
    
    fun setTextAlignment(mode: NSTextAlignment): Unit {
        val sel = ObjCRuntime.sel("setTextAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, mode)
    }
    
    fun setTitleFont(fontObj: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setTitleFont:")
        ObjCRuntime.msgSend(null, ptr, sel, fontObj)
    }
    
    fun setTextFont(fontObj: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setTextFont:")
        ObjCRuntime.msgSend(null, ptr, sel, fontObj)
    }
    
    fun cellAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("cellAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun drawCellAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("drawCellAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun addEntry(title: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("addEntry:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, title) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun addEntry(title: String): MemorySegment = addEntry(ObjCRuntime.newNSString(Arena.global(), title))
    
    fun insertEntry_atIndex(title: MemorySegment, index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("insertEntry:atIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, title, index) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun insertEntry_atIndex(title: String, index: NSInteger): MemorySegment = insertEntry_atIndex(ObjCRuntime.newNSString(Arena.global(), title), index)
    
    fun removeEntryAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeEntryAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun indexOfCellWithTag(tag: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("indexOfCellWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, tag) as NSInteger
    }
    
    fun selectTextAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("selectTextAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun setFrameSize(newSize: NSSize): Unit {
        val sel = ObjCRuntime.sel("setFrameSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(newSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun setTitleBaseWritingDirection(writingDirection: NSWritingDirection): Unit {
        val sel = ObjCRuntime.sel("setTitleBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, writingDirection)
    }
    
    fun setTextBaseWritingDirection(writingDirection: NSWritingDirection): Unit {
        val sel = ObjCRuntime.sel("setTextBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, writingDirection)
    }
    
    fun setPreferredTextFieldWidth(preferredWidth: CGFloat): Unit {
        val sel = ObjCRuntime.sel("setPreferredTextFieldWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, preferredWidth)
    }
    
    fun preferredTextFieldWidth(): CGFloat {
        val sel = ObjCRuntime.sel("preferredTextFieldWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
}


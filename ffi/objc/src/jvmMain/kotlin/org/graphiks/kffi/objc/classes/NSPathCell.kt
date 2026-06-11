/**
 * Kotlin/JVM wrapper for Objective-C class: NSPathCell
 * Superclass: NSActionCell
 * Protocols: NSMenuItemValidation, NSOpenSavePanelDelegate
 */
open class NSPathCell(ptr: MemorySegment) : NSActionCell(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPathCell") }
        
        fun pathComponentCellClass(): Class {
            val sel = ObjCRuntime.sel("pathComponentCellClass")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as Class
        }
        
    }
    
    fun setObjectValue(obj: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setObjectValue:")
        ObjCRuntime.msgSend(null, ptr, sel, obj)
    }
    
    fun rectOfPathComponentCell_withFrame_inView(cell: MemorySegment, frame: NSRect, view: MemorySegment): NSRect {
        val sel = ObjCRuntime.sel("rectOfPathComponentCell:withFrame:inView:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, cell, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view) as NSRect
    }
    
    fun pathComponentCellAtPoint_withFrame_inView(point: NSPoint, frame: NSRect, view: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("pathComponentCellAtPoint:withFrame:inView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view) as MemorySegment
    }
    
    fun mouseEntered_withFrame_inView(event: MemorySegment, frame: NSRect, view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseEntered:withFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, event, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view)
    }
    
    fun mouseExited_withFrame_inView(event: MemorySegment, frame: NSRect, view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("mouseExited:withFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, event, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view)
    }
    
    // @property pathStyle
    fun pathStyle(): NSPathStyle {
        val sel = ObjCRuntime.sel("pathStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPathStyle
    }
    fun setPathStyle(value: NSPathStyle) {
        val sel = ObjCRuntime.sel("setPathStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property URL
    fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowedTypes
    /** @return NSArray<NSString *> * */
    fun allowedTypes(): MemorySegment {
        val sel = ObjCRuntime.sel("allowedTypes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAllowedTypes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAllowedTypes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSPathCellDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pathComponentCellClass
    fun pathComponentCellClass(): Class {
        val sel = ObjCRuntime.sel("pathComponentCellClass")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
    }
    
    // @property pathComponentCells
    /** @return NSArray<__kindof NSPathComponentCell *> * */
    fun pathComponentCells(): MemorySegment {
        val sel = ObjCRuntime.sel("pathComponentCells")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPathComponentCells(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPathComponentCells:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property clickedPathComponentCell
    fun clickedPathComponentCell(): MemorySegment {
        val sel = ObjCRuntime.sel("clickedPathComponentCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property doubleAction
    fun doubleAction(): MemorySegment {
        val sel = ObjCRuntime.sel("doubleAction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDoubleAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDoubleAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property placeholderString
    fun placeholderString(): MemorySegment {
        val sel = ObjCRuntime.sel("placeholderString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPlaceholderString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPlaceholderString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun placeholderStringAsString(): String = ObjCRuntime.toJavaString(placeholderString())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPlaceholderString(value: String) = setPlaceholderString(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property placeholderAttributedString
    fun placeholderAttributedString(): MemorySegment {
        val sel = ObjCRuntime.sel("placeholderAttributedString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPlaceholderAttributedString(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPlaceholderAttributedString:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


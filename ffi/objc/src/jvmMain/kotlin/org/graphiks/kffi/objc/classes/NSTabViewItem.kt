/**
 * Kotlin/JVM wrapper for Objective-C class: NSTabViewItem
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSTabViewItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTabViewItem") }
        
        fun tabViewItemWithViewController(viewController: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("tabViewItemWithViewController:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, viewController) as MemorySegment
        }
        
    }
    
    fun initWithIdentifier(identifier: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    fun drawLabel_inRect(shouldTruncateLabel: BOOL, labelRect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawLabel:inRect:")
        ObjCRuntime.msgSend(null, ptr, sel, shouldTruncateLabel, ObjCRuntime.ObjCStructArg(labelRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun sizeOfLabel(computeMin: BOOL): NSSize {
        val sel = ObjCRuntime.sel("sizeOfLabel:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, computeMin) as NSSize
    }
    
    // @property identifier
    fun identifier(): MemorySegment {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setIdentifier(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property color
    fun color(): MemorySegment {
        val sel = ObjCRuntime.sel("color")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property label
    fun label(): MemorySegment {
        val sel = ObjCRuntime.sel("label")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLabel(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLabel:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun labelAsString(): String = ObjCRuntime.toJavaString(label())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setLabel(value: String) = setLabel(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property view
    fun view(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property viewController
    fun viewController(): MemorySegment {
        val sel = ObjCRuntime.sel("viewController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setViewController(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tabState
    fun tabState(): NSTabState {
        val sel = ObjCRuntime.sel("tabState")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTabState
    }
    
    // @property tabView
    fun tabView(): MemorySegment {
        val sel = ObjCRuntime.sel("tabView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property initialFirstResponder
    fun initialFirstResponder(): MemorySegment {
        val sel = ObjCRuntime.sel("initialFirstResponder")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setInitialFirstResponder(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setInitialFirstResponder:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property toolTip
    fun toolTip(): MemorySegment {
        val sel = ObjCRuntime.sel("toolTip")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setToolTip(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setToolTip:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun toolTipAsString(): String = ObjCRuntime.toJavaString(toolTip())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setToolTip(value: String) = setToolTip(ObjCRuntime.newNSString(Arena.global(), value))
    
}


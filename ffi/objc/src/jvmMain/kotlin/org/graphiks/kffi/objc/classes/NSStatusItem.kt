/**
 * Kotlin/JVM wrapper for Objective-C class: NSStatusItem
 * Superclass: NSObject
 */
open class NSStatusItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSStatusItem") }
        
    }
    
    // @property statusBar
    fun statusBar(): MemorySegment {
        val sel = ObjCRuntime.sel("statusBar")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property length
    fun length(): CGFloat {
        val sel = ObjCRuntime.sel("length")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLength(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLength:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property menu
    fun menu(): MemorySegment {
        val sel = ObjCRuntime.sel("menu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property button
    fun button(): MemorySegment {
        val sel = ObjCRuntime.sel("button")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property behavior
    fun behavior(): NSStatusItemBehavior {
        val sel = ObjCRuntime.sel("behavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSStatusItemBehavior
    }
    fun setBehavior(value: NSStatusItemBehavior) {
        val sel = ObjCRuntime.sel("setBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property visible
    fun isVisible(): BOOL {
        val sel = ObjCRuntime.sel("isVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setVisible(value: BOOL) {
        val sel = ObjCRuntime.sel("setVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property autosaveName
    fun autosaveName(): NSStatusItemAutosaveName {
        val sel = ObjCRuntime.sel("autosaveName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSStatusItemAutosaveName
    }
    fun setAutosaveName(value: NSStatusItemAutosaveName) {
        val sel = ObjCRuntime.sel("setAutosaveName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSStatusItemDeprecated on NSStatusItem ─────────────────────────────────────────

fun NSStatusItem.sendActionOn(mask: NSEventMask): NSInteger {
    val sel = ObjCRuntime.sel("sendActionOn:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, mask) as NSInteger
}

fun NSStatusItem.drawStatusBarBackgroundInRect_withHighlight(rect: NSRect, highlight: BOOL): Unit {
    val sel = ObjCRuntime.sel("drawStatusBarBackgroundInRect:withHighlight:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, highlight)
}

fun NSStatusItem.popUpStatusItemMenu(menu: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("popUpStatusItemMenu:")
    ObjCRuntime.msgSend(null, ptr, sel, menu)
}

fun NSStatusItem.action(): MemorySegment {
    val sel = ObjCRuntime.sel("action")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setAction(action: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAction:")
    ObjCRuntime.msgSend(null, ptr, sel, action)
}

fun NSStatusItem.doubleAction(): MemorySegment {
    val sel = ObjCRuntime.sel("doubleAction")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setDoubleAction(doubleAction: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setDoubleAction:")
    ObjCRuntime.msgSend(null, ptr, sel, doubleAction)
}

fun NSStatusItem.target(): MemorySegment {
    val sel = ObjCRuntime.sel("target")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setTarget(target: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTarget:")
    ObjCRuntime.msgSend(null, ptr, sel, target)
}

fun NSStatusItem.title(): MemorySegment {
    val sel = ObjCRuntime.sel("title")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setTitle(title: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitle:")
    ObjCRuntime.msgSend(null, ptr, sel, title)
}

fun NSStatusItem.attributedTitle(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedTitle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setAttributedTitle(attributedTitle: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributedTitle:")
    ObjCRuntime.msgSend(null, ptr, sel, attributedTitle)
}

fun NSStatusItem.image(): MemorySegment {
    val sel = ObjCRuntime.sel("image")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setImage(image: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setImage:")
    ObjCRuntime.msgSend(null, ptr, sel, image)
}

fun NSStatusItem.alternateImage(): MemorySegment {
    val sel = ObjCRuntime.sel("alternateImage")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setAlternateImage(alternateImage: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAlternateImage:")
    ObjCRuntime.msgSend(null, ptr, sel, alternateImage)
}

fun NSStatusItem.isEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSStatusItem.setEnabled(enabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, enabled)
}

fun NSStatusItem.highlightMode(): BOOL {
    val sel = ObjCRuntime.sel("highlightMode")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSStatusItem.setHighlightMode(highlightMode: BOOL): Unit {
    val sel = ObjCRuntime.sel("setHighlightMode:")
    ObjCRuntime.msgSend(null, ptr, sel, highlightMode)
}

fun NSStatusItem.toolTip(): MemorySegment {
    val sel = ObjCRuntime.sel("toolTip")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setToolTip(toolTip: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setToolTip:")
    ObjCRuntime.msgSend(null, ptr, sel, toolTip)
}

fun NSStatusItem.view(): MemorySegment {
    val sel = ObjCRuntime.sel("view")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSStatusItem.setView(view: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setView:")
    ObjCRuntime.msgSend(null, ptr, sel, view)
}

// @property action
fun NSStatusItem.action(): MemorySegment {
    val sel = ObjCRuntime.sel("action")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setAction(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setAction:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property doubleAction
fun NSStatusItem.doubleAction(): MemorySegment {
    val sel = ObjCRuntime.sel("doubleAction")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setDoubleAction(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setDoubleAction:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property target
fun NSStatusItem.target(): MemorySegment {
    val sel = ObjCRuntime.sel("target")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setTarget(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setTarget:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property title
fun NSStatusItem.title(): MemorySegment {
    val sel = ObjCRuntime.sel("title")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setTitle(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setTitle:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property attributedTitle
fun NSStatusItem.attributedTitle(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedTitle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setAttributedTitle(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setAttributedTitle:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property image
fun NSStatusItem.image(): MemorySegment {
    val sel = ObjCRuntime.sel("image")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setImage(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setImage:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property alternateImage
fun NSStatusItem.alternateImage(): MemorySegment {
    val sel = ObjCRuntime.sel("alternateImage")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setAlternateImage(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setAlternateImage:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property enabled
fun NSStatusItem.isEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSStatusItem.setEnabled(value: BOOL) {
    val sel = ObjCRuntime.sel("setEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property highlightMode
fun NSStatusItem.highlightMode(): BOOL {
    val sel = ObjCRuntime.sel("highlightMode")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
fun NSStatusItem.setHighlightMode(value: BOOL) {
    val sel = ObjCRuntime.sel("setHighlightMode:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property toolTip
fun NSStatusItem.toolTip(): MemorySegment {
    val sel = ObjCRuntime.sel("toolTip")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setToolTip(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setToolTip:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property view
fun NSStatusItem.view(): MemorySegment {
    val sel = ObjCRuntime.sel("view")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSStatusItem.setView(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setView:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}


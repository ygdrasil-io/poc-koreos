package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSControl
 * Superclass: NSView
 */
open class NSControl(ptr: MemorySegment) : NSView(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSControl") }
        
    }
    
    override fun `initWithFrame`(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun sizeThatFits(size: NSSize): NSSize {
        val sel = ObjCRuntime.sel("sizeThatFits:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(size, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"))) as NSSize
    }
    
    fun sizeToFit(): Unit {
        val sel = ObjCRuntime.sel("sizeToFit")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun sendActionOn(mask: NSEventMask): NSInteger {
        val sel = ObjCRuntime.sel("sendActionOn:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, mask) as NSInteger
    }
    
    fun sendAction_to(action: MemorySegment, target: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("sendAction:to:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, action, target) as BOOL
    }
    
    fun takeIntValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeIntValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun takeFloatValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeFloatValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun takeDoubleValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeDoubleValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun takeStringValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeStringValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun takeObjectValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeObjectValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun takeIntegerValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeIntegerValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun performClick(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performClick:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun expansionFrameWithFrame(contentFrame: NSRect): NSRect {
        val sel = ObjCRuntime.sel("expansionFrameWithFrame:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(contentFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    fun drawWithExpansionFrame_inView(contentFrame: NSRect, view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawWithExpansionFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(contentFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view)
    }
    
    // @property target
    fun target(): MemorySegment {
        val sel = ObjCRuntime.sel("target")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTarget(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property action
    fun action(): MemorySegment {
        val sel = ObjCRuntime.sel("action")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tag
    override fun `tag`(): NSInteger {
        val sel = ObjCRuntime.sel("tag")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setTag(value: NSInteger) {
        val sel = ObjCRuntime.sel("setTag:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property ignoresMultiClick
    fun ignoresMultiClick(): BOOL {
        val sel = ObjCRuntime.sel("ignoresMultiClick")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIgnoresMultiClick(value: BOOL) {
        val sel = ObjCRuntime.sel("setIgnoresMultiClick:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property continuous
    fun isContinuous(): BOOL {
        val sel = ObjCRuntime.sel("isContinuous")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setContinuous(value: BOOL) {
        val sel = ObjCRuntime.sel("setContinuous:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property enabled
    fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property refusesFirstResponder
    fun refusesFirstResponder(): BOOL {
        val sel = ObjCRuntime.sel("refusesFirstResponder")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRefusesFirstResponder(value: BOOL) {
        val sel = ObjCRuntime.sel("setRefusesFirstResponder:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property highlighted
    fun isHighlighted(): BOOL {
        val sel = ObjCRuntime.sel("isHighlighted")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHighlighted(value: BOOL) {
        val sel = ObjCRuntime.sel("setHighlighted:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property controlSize
    fun controlSize(): NSControlSize {
        val sel = ObjCRuntime.sel("controlSize")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlSize
    }
    fun setControlSize(value: NSControlSize) {
        val sel = ObjCRuntime.sel("setControlSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property formatter
    fun formatter(): MemorySegment {
        val sel = ObjCRuntime.sel("formatter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFormatter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFormatter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property objectValue
    fun objectValue(): MemorySegment {
        val sel = ObjCRuntime.sel("objectValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setObjectValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setObjectValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property stringValue
    fun stringValue(): MemorySegment {
        val sel = ObjCRuntime.sel("stringValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStringValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStringValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringValueAsString(): String = ObjCRuntime.toJavaString(stringValue())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setStringValue(value: String) = setStringValue(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property attributedStringValue
    fun attributedStringValue(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedStringValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAttributedStringValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedStringValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property intValue
    fun intValue(): Int {
        val sel = ObjCRuntime.sel("intValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    fun setIntValue(value: Int) {
        val sel = ObjCRuntime.sel("setIntValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property integerValue
    fun integerValue(): NSInteger {
        val sel = ObjCRuntime.sel("integerValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setIntegerValue(value: NSInteger) {
        val sel = ObjCRuntime.sel("setIntegerValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property floatValue
    fun floatValue(): Float {
        val sel = ObjCRuntime.sel("floatValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    fun setFloatValue(value: Float) {
        val sel = ObjCRuntime.sel("setFloatValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property doubleValue
    fun doubleValue(): Double {
        val sel = ObjCRuntime.sel("doubleValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    fun setDoubleValue(value: Double) {
        val sel = ObjCRuntime.sel("setDoubleValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property font
    fun font(): MemorySegment {
        val sel = ObjCRuntime.sel("font")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFont(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFont:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesSingleLineMode
    fun usesSingleLineMode(): BOOL {
        val sel = ObjCRuntime.sel("usesSingleLineMode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesSingleLineMode(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesSingleLineMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineBreakMode
    fun lineBreakMode(): NSLineBreakMode {
        val sel = ObjCRuntime.sel("lineBreakMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineBreakMode
    }
    fun setLineBreakMode(value: NSLineBreakMode) {
        val sel = ObjCRuntime.sel("setLineBreakMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alignment
    fun alignment(): NSTextAlignment {
        val sel = ObjCRuntime.sel("alignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
    }
    fun setAlignment(value: NSTextAlignment) {
        val sel = ObjCRuntime.sel("setAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property baseWritingDirection
    fun baseWritingDirection(): NSWritingDirection {
        val sel = ObjCRuntime.sel("baseWritingDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingDirection
    }
    fun setBaseWritingDirection(value: NSWritingDirection) {
        val sel = ObjCRuntime.sel("setBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsExpansionToolTips
    fun allowsExpansionToolTips(): BOOL {
        val sel = ObjCRuntime.sel("allowsExpansionToolTips")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsExpansionToolTips(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsExpansionToolTips:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSControlEditableTextMethods on NSControl ─────────────────────────────────────────

fun NSControl.currentEditor(): MemorySegment {
    val sel = ObjCRuntime.sel("currentEditor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSControl.abortEditing(): BOOL {
    val sel = ObjCRuntime.sel("abortEditing")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSControl.validateEditing(): Unit {
    val sel = ObjCRuntime.sel("validateEditing")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSControl.editWithFrame_editor_delegate_event(rect: NSRect, textObj: MemorySegment, delegate: MemorySegment, event: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("editWithFrame:editor:delegate:event:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, textObj, delegate, event)
}

fun NSControl.selectWithFrame_editor_delegate_start_length(rect: NSRect, textObj: MemorySegment, delegate: MemorySegment, selStart: NSInteger, selLength: NSInteger): Unit {
    val sel = ObjCRuntime.sel("selectWithFrame:editor:delegate:start:length:")
    ObjCRuntime.msgSend(null, ptr, sel, rect, textObj, delegate, selStart, selLength)
}

fun NSControl.endEditing(textObj: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("endEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, textObj)
}

// ── Category: NSDeprecated on NSControl ─────────────────────────────────────────

fun NSControl.setFloatingPointFormat_left_right(autoRange: BOOL, leftDigits: NSUInteger, rightDigits: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setFloatingPointFormat:left:right:")
    ObjCRuntime.msgSend(null, ptr, sel, autoRange, leftDigits, rightDigits)
}

fun NSControl.selectedCell(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSControl.selectedTag(): NSInteger {
    val sel = ObjCRuntime.sel("selectedTag")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSControl.setNeedsDisplay(): Unit {
    val sel = ObjCRuntime.sel("setNeedsDisplay")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSControl.calcSize(): Unit {
    val sel = ObjCRuntime.sel("calcSize")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSControl.updateCell(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("updateCell:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}

fun NSControl.updateCellInside(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("updateCellInside:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}

fun NSControl.drawCellInside(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawCellInside:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}

fun NSControl.drawCell(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawCell:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}

fun NSControl.selectCell(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("selectCell:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}

fun NSControl.cell(): MemorySegment {
    val sel = ObjCRuntime.sel("cell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSControl.setCell(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setCell:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}

// Class<*> method: +[NSControl cellClass]
fun NSControl_cellClass(): Class<*> {
    val sel = ObjCRuntime.sel("cellClass")
    val cls = ObjCRuntime.getClass("NSControl")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as Class<*>
}

// Class<*> method: +[NSControl setCellClass:]
fun NSControl_setCellClass(cellClass: Class<*>): Unit {
    val sel = ObjCRuntime.sel("setCellClass:")
    val cls = ObjCRuntime.getClass("NSControl")
    ObjCRuntime.msgSend(null, cls, sel, cellClass)
}

// @property cellClass
fun NSControl.cellClass(): Class<*> {
    val sel = ObjCRuntime.sel("cellClass")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class<*>
}
fun NSControl.setCellClass(value: Class<*>) {
    val sel = ObjCRuntime.sel("setCellClass:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property cell
    val sel = ObjCRuntime.sel("cell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setCell:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSConstraintBasedLayoutLayering on NSControl ─────────────────────────────────────────

fun NSControl.invalidateIntrinsicContentSizeForCell(cell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("invalidateIntrinsicContentSizeForCell:")
    ObjCRuntime.msgSend(null, ptr, sel, cell)
}


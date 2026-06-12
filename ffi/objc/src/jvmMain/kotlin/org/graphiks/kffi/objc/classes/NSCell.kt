package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSCell
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding, NSUserInterfaceItemIdentification, NSAccessibilityElement, NSAccessibility
 */
open class NSCell(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCell") }
        
        open fun _bulletStringForString_bulletCharacter(string: MemorySegment, bulletChar: unichar): MemorySegment {
            val sel = ObjCRuntime.sel("_bulletStringForString:bulletCharacter:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, string, bulletChar) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        open fun _bulletStringForString_bulletCharacterAsString(string: MemorySegment, bulletChar: unichar): String = ObjCRuntime.toJavaString(_bulletStringForString_bulletCharacter(string, bulletChar))
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun _bulletStringForString_bulletCharacter(string: String, bulletChar: unichar): MemorySegment = _bulletStringForString_bulletCharacter(ObjCRuntime.newNSString(Arena.global(), string), bulletChar)
        
        /** Convenience overload — [String] parameters and [String] return type. */
        open fun _bulletStringForString_bulletCharacterAsString(string: String, bulletChar: unichar): String = ObjCRuntime.toJavaString(_bulletStringForString_bulletCharacter(ObjCRuntime.newNSString(Arena.global(), string), bulletChar))
        
        open fun prefersTrackingUntilMouseUp(): BOOL {
            val sel = ObjCRuntime.sel("prefersTrackingUntilMouseUp")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun defaultMenu(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultMenu")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initTextCell(string: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initTextCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initTextCell(string: String): MemorySegment = initTextCell(ObjCRuntime.newNSString(Arena.global(), string))
    
    open fun initImageCell(image: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initImageCell:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, image) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun sendActionOn(mask: NSEventMask): NSInteger {
        val sel = ObjCRuntime.sel("sendActionOn:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, mask) as NSInteger
    }
    
    open fun compare(otherCell: MemorySegment): NSComparisonResult {
        val sel = ObjCRuntime.sel("compare:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, otherCell) as NSComparisonResult
    }
    
    open fun takeIntValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeIntValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun takeFloatValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeFloatValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun takeDoubleValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeDoubleValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun takeStringValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeStringValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun takeObjectValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeObjectValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun takeIntegerValueFrom(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeIntegerValueFrom:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    open fun cellAttribute(parameter: NSCellAttribute): NSInteger {
        val sel = ObjCRuntime.sel("cellAttribute:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, parameter) as NSInteger
    }
    
    open fun setCellAttribute_to(parameter: NSCellAttribute, value: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setCellAttribute:to:")
        ObjCRuntime.msgSend(null, ptr, sel, parameter, value)
    }
    
    open fun imageRectForBounds(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("imageRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    open fun titleRectForBounds(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("titleRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    open fun drawingRectForBounds(rect: NSRect): NSRect {
        val sel = ObjCRuntime.sel("drawingRectForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSRect
    }
    
    open fun cellSizeForBounds(rect: NSRect): NSSize {
        val sel = ObjCRuntime.sel("cellSizeForBounds:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as NSSize
    }
    
    open fun highlightColorWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("highlightColorWithFrame:inView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView) as MemorySegment
    }
    
    open fun calcDrawInfo(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("calcDrawInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    open fun setUpFieldEditorAttributes(textObj: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("setUpFieldEditorAttributes:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, textObj) as MemorySegment
    }
    
    open fun drawInteriorWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawInteriorWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    open fun drawWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("drawWithFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    open fun highlight_withFrame_inView(flag: BOOL, cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("highlight:withFrame:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, flag, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    open fun getPeriodicDelay_interval(delay: MemorySegment, interval: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getPeriodicDelay:interval:")
        ObjCRuntime.msgSend(null, ptr, sel, delay, interval)
    }
    
    open fun startTrackingAt_inView(startPoint: NSPoint, controlView: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("startTrackingAt:inView:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(startPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), controlView) as BOOL
    }
    
    open fun continueTracking_at_inView(lastPoint: NSPoint, currentPoint: NSPoint, controlView: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("continueTracking:at:inView:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(lastPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(currentPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), controlView) as BOOL
    }
    
    open fun stopTracking_at_inView_mouseIsUp(lastPoint: NSPoint, stopPoint: NSPoint, controlView: MemorySegment, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("stopTracking:at:inView:mouseIsUp:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(lastPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), ObjCRuntime.ObjCStructArg(stopPoint, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), controlView, flag)
    }
    
    open fun trackMouse_inRect_ofView_untilMouseUp(event: MemorySegment, cellFrame: NSRect, controlView: MemorySegment, flag: BOOL): BOOL {
        val sel = ObjCRuntime.sel("trackMouse:inRect:ofView:untilMouseUp:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView, flag) as BOOL
    }
    
    open fun editWithFrame_inView_editor_delegate_event(rect: NSRect, controlView: MemorySegment, textObj: MemorySegment, delegate: MemorySegment, event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("editWithFrame:inView:editor:delegate:event:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView, textObj, delegate, event)
    }
    
    open fun selectWithFrame_inView_editor_delegate_start_length(rect: NSRect, controlView: MemorySegment, textObj: MemorySegment, delegate: MemorySegment, selStart: NSInteger, selLength: NSInteger): Unit {
        val sel = ObjCRuntime.sel("selectWithFrame:inView:editor:delegate:start:length:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView, textObj, delegate, selStart, selLength)
    }
    
    open fun endEditing(textObj: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("endEditing:")
        ObjCRuntime.msgSend(null, ptr, sel, textObj)
    }
    
    open fun resetCursorRect_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("resetCursorRect:inView:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), controlView)
    }
    
    open fun menuForEvent_inRect_ofView(event: MemorySegment, cellFrame: NSRect, view: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("menuForEvent:inRect:ofView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, event, ObjCRuntime.ObjCStructArg(cellFrame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view) as MemorySegment
    }
    
    open fun fieldEditorForView(controlView: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fieldEditorForView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, controlView) as MemorySegment
    }
    
    /** @return NSArray<NSDraggingImageComponent *> * */
    open fun draggingImageComponentsWithFrame_inView(frame: NSRect, view: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("draggingImageComponentsWithFrame:inView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frame, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view) as MemorySegment
    }
    
    // @property prefersTrackingUntilMouseUp
    }
    
    // @property controlView
    open fun controlView(): MemorySegment {
        val sel = ObjCRuntime.sel("controlView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setControlView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setControlView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property type
    open fun type(): NSCellType {
        val sel = ObjCRuntime.sel("type")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCellType
    }
    open fun setType(value: NSCellType) {
        val sel = ObjCRuntime.sel("setType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property state
    open fun state(): NSControlStateValue {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSControlStateValue
    }
    open fun setState(value: NSControlStateValue) {
        val sel = ObjCRuntime.sel("setState:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property target
    open fun target(): MemorySegment {
        val sel = ObjCRuntime.sel("target")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTarget(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property action
    open fun action(): MemorySegment {
        val sel = ObjCRuntime.sel("action")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tag
    open fun tag(): NSInteger {
        val sel = ObjCRuntime.sel("tag")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    open fun setTag(value: NSInteger) {
        val sel = ObjCRuntime.sel("setTag:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property title
    open fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property opaque
    open fun isOpaque(): BOOL {
        val sel = ObjCRuntime.sel("isOpaque")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property enabled
    open fun isEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property continuous
    open fun isContinuous(): BOOL {
        val sel = ObjCRuntime.sel("isContinuous")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setContinuous(value: BOOL) {
        val sel = ObjCRuntime.sel("setContinuous:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property editable
    open fun isEditable(): BOOL {
        val sel = ObjCRuntime.sel("isEditable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setEditable(value: BOOL) {
        val sel = ObjCRuntime.sel("setEditable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectable
    open fun isSelectable(): BOOL {
        val sel = ObjCRuntime.sel("isSelectable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setSelectable(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelectable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bordered
    open fun isBordered(): BOOL {
        val sel = ObjCRuntime.sel("isBordered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setBordered(value: BOOL) {
        val sel = ObjCRuntime.sel("setBordered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bezeled
    open fun isBezeled(): BOOL {
        val sel = ObjCRuntime.sel("isBezeled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setBezeled(value: BOOL) {
        val sel = ObjCRuntime.sel("setBezeled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scrollable
    open fun isScrollable(): BOOL {
        val sel = ObjCRuntime.sel("isScrollable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setScrollable(value: BOOL) {
        val sel = ObjCRuntime.sel("setScrollable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property highlighted
    open fun isHighlighted(): BOOL {
        val sel = ObjCRuntime.sel("isHighlighted")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setHighlighted(value: BOOL) {
        val sel = ObjCRuntime.sel("setHighlighted:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property alignment
    open fun alignment(): NSTextAlignment {
        val sel = ObjCRuntime.sel("alignment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextAlignment
    }
    open fun setAlignment(value: NSTextAlignment) {
        val sel = ObjCRuntime.sel("setAlignment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property wraps
    open fun wraps(): BOOL {
        val sel = ObjCRuntime.sel("wraps")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setWraps(value: BOOL) {
        val sel = ObjCRuntime.sel("setWraps:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property font
    open fun font(): MemorySegment {
        val sel = ObjCRuntime.sel("font")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setFont(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFont:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property keyEquivalent
    open fun keyEquivalent(): MemorySegment {
        val sel = ObjCRuntime.sel("keyEquivalent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun keyEquivalentAsString(): String = ObjCRuntime.toJavaString(keyEquivalent())
    
    // @property formatter
    open fun formatter(): MemorySegment {
        val sel = ObjCRuntime.sel("formatter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setFormatter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFormatter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property objectValue
    open fun objectValue(): MemorySegment {
        val sel = ObjCRuntime.sel("objectValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setObjectValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setObjectValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hasValidObjectValue
    open fun hasValidObjectValue(): BOOL {
        val sel = ObjCRuntime.sel("hasValidObjectValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property stringValue
    open fun stringValue(): MemorySegment {
        val sel = ObjCRuntime.sel("stringValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setStringValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStringValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun stringValueAsString(): String = ObjCRuntime.toJavaString(stringValue())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setStringValue(value: String) = setStringValue(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property intValue
    open fun intValue(): Int {
        val sel = ObjCRuntime.sel("intValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    open fun setIntValue(value: Int) {
        val sel = ObjCRuntime.sel("setIntValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property floatValue
    open fun floatValue(): Float {
        val sel = ObjCRuntime.sel("floatValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    open fun setFloatValue(value: Float) {
        val sel = ObjCRuntime.sel("setFloatValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property doubleValue
    open fun doubleValue(): Double {
        val sel = ObjCRuntime.sel("doubleValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    open fun setDoubleValue(value: Double) {
        val sel = ObjCRuntime.sel("setDoubleValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property integerValue
    open fun integerValue(): NSInteger {
        val sel = ObjCRuntime.sel("integerValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    open fun setIntegerValue(value: NSInteger) {
        val sel = ObjCRuntime.sel("setIntegerValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property image
    open fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property controlSize
    open fun controlSize(): NSControlSize {
        val sel = ObjCRuntime.sel("controlSize")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlSize
    }
    open fun setControlSize(value: NSControlSize) {
        val sel = ObjCRuntime.sel("setControlSize:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property representedObject
    open fun representedObject(): MemorySegment {
        val sel = ObjCRuntime.sel("representedObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setRepresentedObject(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRepresentedObject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property cellSize
    open fun cellSize(): NSSize {
        val sel = ObjCRuntime.sel("cellSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    
    // @property mouseDownFlags
    open fun mouseDownFlags(): NSInteger {
        val sel = ObjCRuntime.sel("mouseDownFlags")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property menu
    open fun menu(): MemorySegment {
        val sel = ObjCRuntime.sel("menu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setMenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property defaultMenu
    }
    
    // @property sendsActionOnEndEditing
    open fun sendsActionOnEndEditing(): BOOL {
        val sel = ObjCRuntime.sel("sendsActionOnEndEditing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setSendsActionOnEndEditing(value: BOOL) {
        val sel = ObjCRuntime.sel("setSendsActionOnEndEditing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property baseWritingDirection
    open fun baseWritingDirection(): NSWritingDirection {
        val sel = ObjCRuntime.sel("baseWritingDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingDirection
    }
    open fun setBaseWritingDirection(value: NSWritingDirection) {
        val sel = ObjCRuntime.sel("setBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineBreakMode
    open fun lineBreakMode(): NSLineBreakMode {
        val sel = ObjCRuntime.sel("lineBreakMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineBreakMode
    }
    open fun setLineBreakMode(value: NSLineBreakMode) {
        val sel = ObjCRuntime.sel("setLineBreakMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsUndo
    open fun allowsUndo(): BOOL {
        val sel = ObjCRuntime.sel("allowsUndo")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsUndo(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsUndo:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property truncatesLastVisibleLine
    open fun truncatesLastVisibleLine(): BOOL {
        val sel = ObjCRuntime.sel("truncatesLastVisibleLine")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setTruncatesLastVisibleLine(value: BOOL) {
        val sel = ObjCRuntime.sel("setTruncatesLastVisibleLine:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property userInterfaceLayoutDirection
    open fun userInterfaceLayoutDirection(): NSUserInterfaceLayoutDirection {
        val sel = ObjCRuntime.sel("userInterfaceLayoutDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceLayoutDirection
    }
    open fun setUserInterfaceLayoutDirection(value: NSUserInterfaceLayoutDirection) {
        val sel = ObjCRuntime.sel("setUserInterfaceLayoutDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property usesSingleLineMode
    open fun usesSingleLineMode(): BOOL {
        val sel = ObjCRuntime.sel("usesSingleLineMode")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setUsesSingleLineMode(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesSingleLineMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSKeyboardUI on NSCell ─────────────────────────────────────────

fun NSCell.performClick(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performClick:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSCell.drawFocusRingMaskWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawFocusRingMaskWithFrame:inView:")
    ObjCRuntime.msgSend(null, ptr, sel, cellFrame, controlView)
}

fun NSCell.focusRingMaskBoundsForFrame_inView(cellFrame: NSRect, controlView: MemorySegment): NSRect {
    val sel = ObjCRuntime.sel("focusRingMaskBoundsForFrame:inView:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, cellFrame, controlView) as NSRect
}

fun NSCell.refusesFirstResponder(): BOOL {
    val sel = ObjCRuntime.sel("refusesFirstResponder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSCell.setRefusesFirstResponder(refusesFirstResponder: BOOL): Unit {
    val sel = ObjCRuntime.sel("setRefusesFirstResponder:")
    ObjCRuntime.msgSend(null, ptr, sel, refusesFirstResponder)
}

fun NSCell.acceptsFirstResponder(): BOOL {
    val sel = ObjCRuntime.sel("acceptsFirstResponder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSCell.showsFirstResponder(): BOOL {
    val sel = ObjCRuntime.sel("showsFirstResponder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSCell.setShowsFirstResponder(showsFirstResponder: BOOL): Unit {
    val sel = ObjCRuntime.sel("setShowsFirstResponder:")
    ObjCRuntime.msgSend(null, ptr, sel, showsFirstResponder)
}

fun NSCell.focusRingType(): NSFocusRingType {
    val sel = ObjCRuntime.sel("focusRingType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFocusRingType
}

fun NSCell.setFocusRingType(focusRingType: NSFocusRingType): Unit {
    val sel = ObjCRuntime.sel("setFocusRingType:")
    ObjCRuntime.msgSend(null, ptr, sel, focusRingType)
}

fun NSCell.wantsNotificationForMarkedText(): BOOL {
    val sel = ObjCRuntime.sel("wantsNotificationForMarkedText")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// Class<*> method: +[NSCell defaultFocusRingType]
fun NSCell_defaultFocusRingType(): NSFocusRingType {
    val sel = ObjCRuntime.sel("defaultFocusRingType")
    val cls = ObjCRuntime.getClass("NSCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as NSFocusRingType
}

// @property refusesFirstResponder
    val sel = ObjCRuntime.sel("refusesFirstResponder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setRefusesFirstResponder:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property acceptsFirstResponder
    val sel = ObjCRuntime.sel("acceptsFirstResponder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property showsFirstResponder
    val sel = ObjCRuntime.sel("showsFirstResponder")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setShowsFirstResponder:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property focusRingType
    val sel = ObjCRuntime.sel("focusRingType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFocusRingType
}
    val sel = ObjCRuntime.sel("setFocusRingType:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property defaultFocusRingType
fun NSCell.defaultFocusRingType(): NSFocusRingType {
    val sel = ObjCRuntime.sel("defaultFocusRingType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFocusRingType
}

// @property wantsNotificationForMarkedText
    val sel = ObjCRuntime.sel("wantsNotificationForMarkedText")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSCellAttributedStringMethods on NSCell ─────────────────────────────────────────

fun NSCell.attributedStringValue(): MemorySegment {
    val sel = ObjCRuntime.sel("attributedStringValue")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSCell.setAttributedStringValue(attributedStringValue: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAttributedStringValue:")
    ObjCRuntime.msgSend(null, ptr, sel, attributedStringValue)
}

fun NSCell.allowsEditingTextAttributes(): BOOL {
    val sel = ObjCRuntime.sel("allowsEditingTextAttributes")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSCell.setAllowsEditingTextAttributes(allowsEditingTextAttributes: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsEditingTextAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsEditingTextAttributes)
}

fun NSCell.importsGraphics(): BOOL {
    val sel = ObjCRuntime.sel("importsGraphics")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSCell.setImportsGraphics(importsGraphics: BOOL): Unit {
    val sel = ObjCRuntime.sel("setImportsGraphics:")
    ObjCRuntime.msgSend(null, ptr, sel, importsGraphics)
}

// @property attributedStringValue
    val sel = ObjCRuntime.sel("attributedStringValue")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setAttributedStringValue:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property allowsEditingTextAttributes
    val sel = ObjCRuntime.sel("allowsEditingTextAttributes")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setAllowsEditingTextAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property importsGraphics
    val sel = ObjCRuntime.sel("importsGraphics")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setImportsGraphics:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSCellMixedState on NSCell ─────────────────────────────────────────

fun NSCell.setNextState(): Unit {
    val sel = ObjCRuntime.sel("setNextState")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSCell.allowsMixedState(): BOOL {
    val sel = ObjCRuntime.sel("allowsMixedState")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSCell.setAllowsMixedState(allowsMixedState: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsMixedState:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsMixedState)
}

fun NSCell.nextState(): NSInteger {
    val sel = ObjCRuntime.sel("nextState")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

// @property allowsMixedState
    val sel = ObjCRuntime.sel("allowsMixedState")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setAllowsMixedState:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property nextState
    val sel = ObjCRuntime.sel("nextState")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

// ── Category: NSCellHitTest on NSCell ─────────────────────────────────────────

fun NSCell.hitTestForEvent_inRect_ofView(event: MemorySegment, cellFrame: NSRect, controlView: MemorySegment): NSCellHitResult {
    val sel = ObjCRuntime.sel("hitTestForEvent:inRect:ofView:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, event, cellFrame, controlView) as NSCellHitResult
}

// ── Category: NSCellExpansion on NSCell ─────────────────────────────────────────

fun NSCell.expansionFrameWithFrame_inView(cellFrame: NSRect, view: MemorySegment): NSRect {
    val sel = ObjCRuntime.sel("expansionFrameWithFrame:inView:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel, cellFrame, view) as NSRect
}

fun NSCell.drawWithExpansionFrame_inView(cellFrame: NSRect, view: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("drawWithExpansionFrame:inView:")
    ObjCRuntime.msgSend(null, ptr, sel, cellFrame, view)
}

// ── Category: NSCellBackgroundStyle on NSCell ─────────────────────────────────────────

fun NSCell.backgroundStyle(): NSBackgroundStyle {
    val sel = ObjCRuntime.sel("backgroundStyle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBackgroundStyle
}

fun NSCell.setBackgroundStyle(backgroundStyle: NSBackgroundStyle): Unit {
    val sel = ObjCRuntime.sel("setBackgroundStyle:")
    ObjCRuntime.msgSend(null, ptr, sel, backgroundStyle)
}

fun NSCell.interiorBackgroundStyle(): NSBackgroundStyle {
    val sel = ObjCRuntime.sel("interiorBackgroundStyle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBackgroundStyle
}

// @property backgroundStyle
    val sel = ObjCRuntime.sel("backgroundStyle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBackgroundStyle
}
    val sel = ObjCRuntime.sel("setBackgroundStyle:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property interiorBackgroundStyle
    val sel = ObjCRuntime.sel("interiorBackgroundStyle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSBackgroundStyle
}

// ── Category: NSDeprecated on NSCell ─────────────────────────────────────────

fun NSCell.entryType(): NSInteger {
    val sel = ObjCRuntime.sel("entryType")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSCell.setEntryType(type: NSInteger): Unit {
    val sel = ObjCRuntime.sel("setEntryType:")
    ObjCRuntime.msgSend(null, ptr, sel, type)
}

fun NSCell.isEntryAcceptable(string: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEntryAcceptable:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string) as BOOL
}

fun NSCell.setFloatingPointFormat_left_right(autoRange: BOOL, leftDigits: NSUInteger, rightDigits: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setFloatingPointFormat:left:right:")
    ObjCRuntime.msgSend(null, ptr, sel, autoRange, leftDigits, rightDigits)
}

fun NSCell.setMnemonicLocation(location: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setMnemonicLocation:")
    ObjCRuntime.msgSend(null, ptr, sel, location)
}

fun NSCell.mnemonicLocation(): NSUInteger {
    val sel = ObjCRuntime.sel("mnemonicLocation")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSCell.mnemonic(): MemorySegment {
    val sel = ObjCRuntime.sel("mnemonic")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSCell.setTitleWithMnemonic(stringWithAmpersand: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleWithMnemonic:")
    ObjCRuntime.msgSend(null, ptr, sel, stringWithAmpersand)
}

fun NSCell.controlTint(): NSControlTint {
    val sel = ObjCRuntime.sel("controlTint")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlTint
}

fun NSCell.setControlTint(controlTint: NSControlTint): Unit {
    val sel = ObjCRuntime.sel("setControlTint:")
    ObjCRuntime.msgSend(null, ptr, sel, controlTint)
}

// @property controlTint
    val sel = ObjCRuntime.sel("controlTint")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlTint
}
    val sel = ObjCRuntime.sel("setControlTint:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}


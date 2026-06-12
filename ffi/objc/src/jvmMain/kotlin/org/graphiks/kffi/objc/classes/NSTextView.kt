package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextView
 * Superclass: NSText
 * Protocols: NSColorChanging, NSMenuItemValidation, NSUserInterfaceValidations, NSTextInputClient, NSTextLayoutOrientationProvider, NSDraggingSource, NSStandardKeyBindingResponding, NSTextInput, NSAccessibilityNavigableStaticText, NSTextContent
 */
open class NSTextView(ptr: MemorySegment) : NSText(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextView") }
        
        fun textViewUsingTextLayoutManager(usingTextLayoutManager: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("textViewUsingTextLayoutManager:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, usingTextLayoutManager) as MemorySegment
        }
        
        fun stronglyReferencesTextStorage(): BOOL {
            val sel = ObjCRuntime.sel("stronglyReferencesTextStorage")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    fun initWithFrame_textContainer(frameRect: NSRect, container: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:textContainer:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), container) as MemorySegment
    }
    
    override fun `initWithCoder`(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    override fun `initWithFrame`(frameRect: NSRect): MemorySegment {
        val sel = ObjCRuntime.sel("initWithFrame:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(frameRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as MemorySegment
    }
    
    fun initUsingTextLayoutManager(usingTextLayoutManager: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initUsingTextLayoutManager:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, usingTextLayoutManager) as MemorySegment
    }
    
    fun replaceTextContainer(newContainer: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("replaceTextContainer:")
        ObjCRuntime.msgSend(null, ptr, sel, newContainer)
    }
    
    fun invalidateTextContainerOrigin(): Unit {
        val sel = ObjCRuntime.sel("invalidateTextContainerOrigin")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun insertText(insertString: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("insertText:")
        ObjCRuntime.msgSend(null, ptr, sel, insertString)
    }
    
    fun setConstrainedFrameSize(desiredSize: NSSize): Unit {
        val sel = ObjCRuntime.sel("setConstrainedFrameSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(desiredSize, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    fun setAlignment_range(alignment: NSTextAlignment, range: NSRange): Unit {
        val sel = ObjCRuntime.sel("setAlignment:range:")
        ObjCRuntime.msgSend(null, ptr, sel, alignment, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun setBaseWritingDirection_range(writingDirection: NSWritingDirection, range: NSRange): Unit {
        val sel = ObjCRuntime.sel("setBaseWritingDirection:range:")
        ObjCRuntime.msgSend(null, ptr, sel, writingDirection, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")))
    }
    
    fun turnOffKerning(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("turnOffKerning:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun tightenKerning(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("tightenKerning:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun loosenKerning(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("loosenKerning:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun useStandardKerning(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("useStandardKerning:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun turnOffLigatures(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("turnOffLigatures:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun useStandardLigatures(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("useStandardLigatures:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun useAllLigatures(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("useAllLigatures:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun raiseBaseline(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("raiseBaseline:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun lowerBaseline(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("lowerBaseline:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun toggleTraditionalCharacterShape(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("toggleTraditionalCharacterShape:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun outline(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("outline:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun performFindPanelAction(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performFindPanelAction:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun alignJustified(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("alignJustified:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun changeColor(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("changeColor:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun changeAttributes(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("changeAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun changeDocumentBackgroundColor(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("changeDocumentBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderFrontSpacingPanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderFrontSpacingPanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderFrontLinkPanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderFrontLinkPanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderFrontListPanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderFrontListPanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun orderFrontTablePanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("orderFrontTablePanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun rulerView_didMoveMarker(ruler: MemorySegment, marker: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("rulerView:didMoveMarker:")
        ObjCRuntime.msgSend(null, ptr, sel, ruler, marker)
    }
    
    fun rulerView_didRemoveMarker(ruler: MemorySegment, marker: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("rulerView:didRemoveMarker:")
        ObjCRuntime.msgSend(null, ptr, sel, ruler, marker)
    }
    
    fun rulerView_didAddMarker(ruler: MemorySegment, marker: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("rulerView:didAddMarker:")
        ObjCRuntime.msgSend(null, ptr, sel, ruler, marker)
    }
    
    fun rulerView_shouldMoveMarker(ruler: MemorySegment, marker: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("rulerView:shouldMoveMarker:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ruler, marker) as BOOL
    }
    
    fun rulerView_shouldAddMarker(ruler: MemorySegment, marker: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("rulerView:shouldAddMarker:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ruler, marker) as BOOL
    }
    
    fun rulerView_willMoveMarker_toLocation(ruler: MemorySegment, marker: MemorySegment, location: CGFloat): CGFloat {
        val sel = ObjCRuntime.sel("rulerView:willMoveMarker:toLocation:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ruler, marker, location) as CGFloat
    }
    
    fun rulerView_shouldRemoveMarker(ruler: MemorySegment, marker: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("rulerView:shouldRemoveMarker:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ruler, marker) as BOOL
    }
    
    fun rulerView_willAddMarker_atLocation(ruler: MemorySegment, marker: MemorySegment, location: CGFloat): CGFloat {
        val sel = ObjCRuntime.sel("rulerView:willAddMarker:atLocation:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, ruler, marker, location) as CGFloat
    }
    
    fun rulerView_handleMouseDown(ruler: MemorySegment, event: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("rulerView:handleMouseDown:")
        ObjCRuntime.msgSend(null, ptr, sel, ruler, event)
    }
    
    fun setNeedsDisplayInRect_avoidAdditionalLayout(rect: NSRect, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setNeedsDisplayInRect:avoidAdditionalLayout:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), flag)
    }
    
    fun drawInsertionPointInRect_color_turnedOn(rect: NSRect, color: MemorySegment, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("drawInsertionPointInRect:color:turnedOn:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), color, flag)
    }
    
    fun drawViewBackgroundInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawViewBackgroundInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    fun updateRuler(): Unit {
        val sel = ObjCRuntime.sel("updateRuler")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun updateFontPanel(): Unit {
        val sel = ObjCRuntime.sel("updateFontPanel")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun updateDragTypeRegistration(): Unit {
        val sel = ObjCRuntime.sel("updateDragTypeRegistration")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun selectionRangeForProposedRange_granularity(proposedCharRange: NSRange, granularity: NSSelectionGranularity): NSRange {
        val sel = ObjCRuntime.sel("selectionRangeForProposedRange:granularity:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, ObjCRuntime.ObjCStructArg(proposedCharRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), granularity) as NSRange
    }
    
    fun clickedOnLink_atIndex(link: MemorySegment, charIndex: NSUInteger): Unit {
        val sel = ObjCRuntime.sel("clickedOnLink:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, link, charIndex)
    }
    
    fun startSpeaking(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("startSpeaking:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun stopSpeaking(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("stopSpeaking:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun setLayoutOrientation(orientation: NSTextLayoutOrientation): Unit {
        val sel = ObjCRuntime.sel("setLayoutOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, orientation)
    }
    
    fun changeLayoutOrientation(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("changeLayoutOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun characterIndexForInsertionAtPoint(point: NSPoint): NSUInteger {
        val sel = ObjCRuntime.sel("characterIndexForInsertionAtPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as NSUInteger
    }
    
    fun performValidatedReplacementInRange_withAttributedString(range: NSRange, attributedString: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("performValidatedReplacementInRange:withAttributedString:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), attributedString) as BOOL
    }
    
    // @property textContainer
    fun textContainer(): MemorySegment {
        val sel = ObjCRuntime.sel("textContainer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextContainer(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextContainer:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textContainerInset
    fun textContainerInset(): NSSize {
        val sel = ObjCRuntime.sel("textContainerInset")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setTextContainerInset(value: NSSize) {
        val sel = ObjCRuntime.sel("setTextContainerInset:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property textContainerOrigin
    fun textContainerOrigin(): NSPoint {
        val sel = ObjCRuntime.sel("textContainerOrigin")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
    // @property layoutManager
    fun layoutManager(): MemorySegment {
        val sel = ObjCRuntime.sel("layoutManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textStorage
    fun textStorage(): MemorySegment {
        val sel = ObjCRuntime.sel("textStorage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textLayoutManager
    fun textLayoutManager(): MemorySegment {
        val sel = ObjCRuntime.sel("textLayoutManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textContentStorage
    fun textContentStorage(): MemorySegment {
        val sel = ObjCRuntime.sel("textContentStorage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shouldDrawInsertionPoint
    fun shouldDrawInsertionPoint(): BOOL {
        val sel = ObjCRuntime.sel("shouldDrawInsertionPoint")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property stronglyReferencesTextStorage
    fun usesAdaptiveColorMappingForDarkAppearance(): BOOL {
        val sel = ObjCRuntime.sel("usesAdaptiveColorMappingForDarkAppearance")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setUsesAdaptiveColorMappingForDarkAppearance(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesAdaptiveColorMappingForDarkAppearance:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSCompletion on NSTextView ─────────────────────────────────────────

fun NSTextView.complete(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("complete:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

/** @return NSArray<NSString *> * */
fun NSTextView.completionsForPartialWordRange_indexOfSelectedItem(charRange: NSRange, index: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("completionsForPartialWordRange:indexOfSelectedItem:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, charRange, index) as MemorySegment
}

fun NSTextView.insertCompletion_forPartialWordRange_movement_isFinal(word: MemorySegment, charRange: NSRange, movement: NSInteger, flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("insertCompletion:forPartialWordRange:movement:isFinal:")
    ObjCRuntime.msgSend(null, ptr, sel, word, charRange, movement, flag)
}

fun NSTextView.rangeForUserCompletion(): NSRange {
    val sel = ObjCRuntime.sel("rangeForUserCompletion")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
}

// @property rangeForUserCompletion
fun NSTextView.writeSelectionToPasteboard_type(pboard: MemorySegment, type: NSPasteboardType): BOOL {
    val sel = ObjCRuntime.sel("writeSelectionToPasteboard:type:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, pboard, type) as BOOL
}

fun NSTextView.writeSelectionToPasteboard_types(pboard: MemorySegment, types: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("writeSelectionToPasteboard:types:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, pboard, types) as BOOL
}

fun NSTextView.preferredPasteboardTypeFromArray_restrictedToTypesFromArray(availableTypes: MemorySegment, allowedTypes: MemorySegment): NSPasteboardType {
    val sel = ObjCRuntime.sel("preferredPasteboardTypeFromArray:restrictedToTypesFromArray:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, availableTypes, allowedTypes) as NSPasteboardType
}

fun NSTextView.readSelectionFromPasteboard_type(pboard: MemorySegment, type: NSPasteboardType): BOOL {
    val sel = ObjCRuntime.sel("readSelectionFromPasteboard:type:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, pboard, type) as BOOL
}

fun NSTextView.readSelectionFromPasteboard(pboard: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("readSelectionFromPasteboard:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, pboard) as BOOL
}

fun NSTextView.validRequestorForSendType_returnType(sendType: NSPasteboardType, returnType: NSPasteboardType): MemorySegment {
    val sel = ObjCRuntime.sel("validRequestorForSendType:returnType:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sendType, returnType) as MemorySegment
}

fun NSTextView.pasteAsPlainText(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("pasteAsPlainText:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.pasteAsRichText(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("pasteAsRichText:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

/** @return NSArray<NSPasteboardType> * */
fun NSTextView.writablePasteboardTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("writablePasteboardTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSPasteboardType> * */
fun NSTextView.readablePasteboardTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("readablePasteboardTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class<*> method: +[NSTextView registerForServices]
fun NSTextView_registerForServices(): Unit {
    val sel = ObjCRuntime.sel("registerForServices")
    val cls = ObjCRuntime.getClass("NSTextView")
    ObjCRuntime.msgSend(null, cls, sel)
}

// @property writablePasteboardTypes
/** @return NSArray<NSPasteboardType> * */
/** @return NSArray<NSPasteboardType> * */
fun NSTextView.dragSelectionWithEvent_offset_slideBack(event: MemorySegment, mouseOffset: NSSize, slideBack: BOOL): BOOL {
    val sel = ObjCRuntime.sel("dragSelectionWithEvent:offset:slideBack:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event, mouseOffset, slideBack) as BOOL
}

fun NSTextView.dragImageForSelectionWithEvent_origin(event: MemorySegment, origin: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dragImageForSelectionWithEvent:origin:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, event, origin) as MemorySegment
}

fun NSTextView.dragOperationForDraggingInfo_type(dragInfo: MemorySegment, type: NSPasteboardType): NSDragOperation {
    val sel = ObjCRuntime.sel("dragOperationForDraggingInfo:type:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dragInfo, type) as NSDragOperation
}

fun NSTextView.cleanUpAfterDragOperation(): Unit {
    val sel = ObjCRuntime.sel("cleanUpAfterDragOperation")
    ObjCRuntime.msgSend(null, ptr, sel)
}

/** @return NSArray<NSPasteboardType> * */
fun NSTextView.acceptableDragTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("acceptableDragTypes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property acceptableDragTypes
/** @return NSArray<NSPasteboardType> * */
fun NSTextView.setSelectedRanges_affinity_stillSelecting(ranges: MemorySegment, affinity: NSSelectionAffinity, stillSelectingFlag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setSelectedRanges:affinity:stillSelecting:")
    ObjCRuntime.msgSend(null, ptr, sel, ranges, affinity, stillSelectingFlag)
}

fun NSTextView.setSelectedRange_affinity_stillSelecting(charRange: NSRange, affinity: NSSelectionAffinity, stillSelectingFlag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setSelectedRange:affinity:stillSelecting:")
    ObjCRuntime.msgSend(null, ptr, sel, charRange, affinity, stillSelectingFlag)
}

fun NSTextView.updateInsertionPointStateAndRestartTimer(restartFlag: BOOL): Unit {
    val sel = ObjCRuntime.sel("updateInsertionPointStateAndRestartTimer:")
    ObjCRuntime.msgSend(null, ptr, sel, restartFlag)
}

fun NSTextView.toggleContinuousSpellChecking(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleContinuousSpellChecking:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.toggleGrammarChecking(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleGrammarChecking:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.setSpellingState_range(value: NSInteger, charRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setSpellingState:range:")
    ObjCRuntime.msgSend(null, ptr, sel, value, charRange)
}

fun NSTextView.shouldChangeTextInRanges_replacementStrings(affectedRanges: MemorySegment, replacementStrings: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("shouldChangeTextInRanges:replacementStrings:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, affectedRanges, replacementStrings) as BOOL
}

fun NSTextView.shouldChangeTextInRange_replacementString(affectedCharRange: NSRange, replacementString: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("shouldChangeTextInRange:replacementString:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, affectedCharRange, replacementString) as BOOL
}

fun NSTextView.didChangeText(): Unit {
    val sel = ObjCRuntime.sel("didChangeText")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSTextView.breakUndoCoalescing(): Unit {
    val sel = ObjCRuntime.sel("breakUndoCoalescing")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSTextView.showFindIndicatorForRange(charRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("showFindIndicatorForRange:")
    ObjCRuntime.msgSend(null, ptr, sel, charRange)
}

fun NSTextView.setSelectedRange(charRange: NSRange): Unit {
    val sel = ObjCRuntime.sel("setSelectedRange:")
    ObjCRuntime.msgSend(null, ptr, sel, charRange)
}

/** @return NSArray<NSValue *> * */
fun NSTextView.selectedRanges(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedRanges")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setSelectedRanges(selectedRanges: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setSelectedRanges:")
    ObjCRuntime.msgSend(null, ptr, sel, selectedRanges)
}

fun NSTextView.selectionAffinity(): NSSelectionAffinity {
    val sel = ObjCRuntime.sel("selectionAffinity")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSelectionAffinity
}

fun NSTextView.selectionGranularity(): NSSelectionGranularity {
    val sel = ObjCRuntime.sel("selectionGranularity")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSelectionGranularity
}

fun NSTextView.setSelectionGranularity(selectionGranularity: NSSelectionGranularity): Unit {
    val sel = ObjCRuntime.sel("setSelectionGranularity:")
    ObjCRuntime.msgSend(null, ptr, sel, selectionGranularity)
}

/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSTextView.selectedTextAttributes(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedTextAttributes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setSelectedTextAttributes(selectedTextAttributes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setSelectedTextAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, selectedTextAttributes)
}

fun NSTextView.insertionPointColor(): MemorySegment {
    val sel = ObjCRuntime.sel("insertionPointColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setInsertionPointColor(insertionPointColor: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setInsertionPointColor:")
    ObjCRuntime.msgSend(null, ptr, sel, insertionPointColor)
}

/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSTextView.markedTextAttributes(): MemorySegment {
    val sel = ObjCRuntime.sel("markedTextAttributes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setMarkedTextAttributes(markedTextAttributes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setMarkedTextAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, markedTextAttributes)
}

/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSTextView.linkTextAttributes(): MemorySegment {
    val sel = ObjCRuntime.sel("linkTextAttributes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setLinkTextAttributes(linkTextAttributes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setLinkTextAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, linkTextAttributes)
}

fun NSTextView.displaysLinkToolTips(): BOOL {
    val sel = ObjCRuntime.sel("displaysLinkToolTips")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setDisplaysLinkToolTips(displaysLinkToolTips: BOOL): Unit {
    val sel = ObjCRuntime.sel("setDisplaysLinkToolTips:")
    ObjCRuntime.msgSend(null, ptr, sel, displaysLinkToolTips)
}

fun NSTextView.acceptsGlyphInfo(): BOOL {
    val sel = ObjCRuntime.sel("acceptsGlyphInfo")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAcceptsGlyphInfo(acceptsGlyphInfo: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAcceptsGlyphInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, acceptsGlyphInfo)
}

fun NSTextView.usesRuler(): BOOL {
    val sel = ObjCRuntime.sel("usesRuler")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setUsesRuler(usesRuler: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesRuler:")
    ObjCRuntime.msgSend(null, ptr, sel, usesRuler)
}

fun NSTextView.usesInspectorBar(): BOOL {
    val sel = ObjCRuntime.sel("usesInspectorBar")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setUsesInspectorBar(usesInspectorBar: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesInspectorBar:")
    ObjCRuntime.msgSend(null, ptr, sel, usesInspectorBar)
}

fun NSTextView.isContinuousSpellCheckingEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isContinuousSpellCheckingEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setContinuousSpellCheckingEnabled(continuousSpellCheckingEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setContinuousSpellCheckingEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, continuousSpellCheckingEnabled)
}

fun NSTextView.spellCheckerDocumentTag(): NSInteger {
    val sel = ObjCRuntime.sel("spellCheckerDocumentTag")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSTextView.isGrammarCheckingEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isGrammarCheckingEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setGrammarCheckingEnabled(grammarCheckingEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setGrammarCheckingEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, grammarCheckingEnabled)
}

/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSTextView.typingAttributes(): MemorySegment {
    val sel = ObjCRuntime.sel("typingAttributes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setTypingAttributes(typingAttributes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTypingAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, typingAttributes)
}

/** @return NSArray<NSValue *> * */
fun NSTextView.rangesForUserTextChange(): MemorySegment {
    val sel = ObjCRuntime.sel("rangesForUserTextChange")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSValue *> * */
fun NSTextView.rangesForUserCharacterAttributeChange(): MemorySegment {
    val sel = ObjCRuntime.sel("rangesForUserCharacterAttributeChange")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSValue *> * */
fun NSTextView.rangesForUserParagraphAttributeChange(): MemorySegment {
    val sel = ObjCRuntime.sel("rangesForUserParagraphAttributeChange")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.rangeForUserTextChange(): NSRange {
    val sel = ObjCRuntime.sel("rangeForUserTextChange")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
}

fun NSTextView.rangeForUserCharacterAttributeChange(): NSRange {
    val sel = ObjCRuntime.sel("rangeForUserCharacterAttributeChange")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
}

fun NSTextView.rangeForUserParagraphAttributeChange(): NSRange {
    val sel = ObjCRuntime.sel("rangeForUserParagraphAttributeChange")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
}

fun NSTextView.allowsDocumentBackgroundColorChange(): BOOL {
    val sel = ObjCRuntime.sel("allowsDocumentBackgroundColorChange")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAllowsDocumentBackgroundColorChange(allowsDocumentBackgroundColorChange: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsDocumentBackgroundColorChange:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsDocumentBackgroundColorChange)
}

fun NSTextView.defaultParagraphStyle(): MemorySegment {
    val sel = ObjCRuntime.sel("defaultParagraphStyle")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setDefaultParagraphStyle(defaultParagraphStyle: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setDefaultParagraphStyle:")
    ObjCRuntime.msgSend(null, ptr, sel, defaultParagraphStyle)
}

fun NSTextView.allowsUndo(): BOOL {
    val sel = ObjCRuntime.sel("allowsUndo")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAllowsUndo(allowsUndo: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsUndo:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsUndo)
}

fun NSTextView.isCoalescingUndo(): BOOL {
    val sel = ObjCRuntime.sel("isCoalescingUndo")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.allowsImageEditing(): BOOL {
    val sel = ObjCRuntime.sel("allowsImageEditing")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAllowsImageEditing(allowsImageEditing: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsImageEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsImageEditing)
}

fun NSTextView.usesRolloverButtonForSelection(): BOOL {
    val sel = ObjCRuntime.sel("usesRolloverButtonForSelection")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setUsesRolloverButtonForSelection(usesRolloverButtonForSelection: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesRolloverButtonForSelection:")
    ObjCRuntime.msgSend(null, ptr, sel, usesRolloverButtonForSelection)
}

/** @return id<NSTextViewDelegate> */
fun NSTextView.delegate(): MemorySegment {
    val sel = ObjCRuntime.sel("delegate")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setDelegate(delegate: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setDelegate:")
    ObjCRuntime.msgSend(null, ptr, sel, delegate)
}

fun NSTextView.isEditable(): BOOL {
    val sel = ObjCRuntime.sel("isEditable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setEditable(editable: BOOL): Unit {
    val sel = ObjCRuntime.sel("setEditable:")
    ObjCRuntime.msgSend(null, ptr, sel, editable)
}

fun NSTextView.isSelectable(): BOOL {
    val sel = ObjCRuntime.sel("isSelectable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setSelectable(selectable: BOOL): Unit {
    val sel = ObjCRuntime.sel("setSelectable:")
    ObjCRuntime.msgSend(null, ptr, sel, selectable)
}

fun NSTextView.isRichText(): BOOL {
    val sel = ObjCRuntime.sel("isRichText")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setRichText(richText: BOOL): Unit {
    val sel = ObjCRuntime.sel("setRichText:")
    ObjCRuntime.msgSend(null, ptr, sel, richText)
}

fun NSTextView.importsGraphics(): BOOL {
    val sel = ObjCRuntime.sel("importsGraphics")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setImportsGraphics(importsGraphics: BOOL): Unit {
    val sel = ObjCRuntime.sel("setImportsGraphics:")
    ObjCRuntime.msgSend(null, ptr, sel, importsGraphics)
}

fun NSTextView.drawsBackground(): BOOL {
    val sel = ObjCRuntime.sel("drawsBackground")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setDrawsBackground(drawsBackground: BOOL): Unit {
    val sel = ObjCRuntime.sel("setDrawsBackground:")
    ObjCRuntime.msgSend(null, ptr, sel, drawsBackground)
}

fun NSTextView.backgroundColor(): MemorySegment {
    val sel = ObjCRuntime.sel("backgroundColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setBackgroundColor(backgroundColor: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setBackgroundColor:")
    ObjCRuntime.msgSend(null, ptr, sel, backgroundColor)
}

fun NSTextView.isFieldEditor(): BOOL {
    val sel = ObjCRuntime.sel("isFieldEditor")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setFieldEditor(fieldEditor: BOOL): Unit {
    val sel = ObjCRuntime.sel("setFieldEditor:")
    ObjCRuntime.msgSend(null, ptr, sel, fieldEditor)
}

fun NSTextView.usesFontPanel(): BOOL {
    val sel = ObjCRuntime.sel("usesFontPanel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setUsesFontPanel(usesFontPanel: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesFontPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, usesFontPanel)
}

fun NSTextView.isRulerVisible(): BOOL {
    val sel = ObjCRuntime.sel("isRulerVisible")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setRulerVisible(rulerVisible: BOOL): Unit {
    val sel = ObjCRuntime.sel("setRulerVisible:")
    ObjCRuntime.msgSend(null, ptr, sel, rulerVisible)
}

/** @return NSArray<NSString *> * */
fun NSTextView.allowedInputSourceLocales(): MemorySegment {
    val sel = ObjCRuntime.sel("allowedInputSourceLocales")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setAllowedInputSourceLocales(allowedInputSourceLocales: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setAllowedInputSourceLocales:")
    ObjCRuntime.msgSend(null, ptr, sel, allowedInputSourceLocales)
}

fun NSTextView.isWritingToolsActive(): BOOL {
    val sel = ObjCRuntime.sel("isWritingToolsActive")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.writingToolsBehavior(): NSWritingToolsBehavior {
    val sel = ObjCRuntime.sel("writingToolsBehavior")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingToolsBehavior
}

fun NSTextView.setWritingToolsBehavior(writingToolsBehavior: NSWritingToolsBehavior): Unit {
    val sel = ObjCRuntime.sel("setWritingToolsBehavior:")
    ObjCRuntime.msgSend(null, ptr, sel, writingToolsBehavior)
}

fun NSTextView.allowedWritingToolsResultOptions(): NSWritingToolsResultOptions {
    val sel = ObjCRuntime.sel("allowedWritingToolsResultOptions")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingToolsResultOptions
}

fun NSTextView.setAllowedWritingToolsResultOptions(allowedWritingToolsResultOptions: NSWritingToolsResultOptions): Unit {
    val sel = ObjCRuntime.sel("setAllowedWritingToolsResultOptions:")
    ObjCRuntime.msgSend(null, ptr, sel, allowedWritingToolsResultOptions)
}

// @property selectedRanges
/** @return NSArray<NSValue *> * */
/** @return NSDictionary<NSAttributedStringKey,id> * */
/** @return NSDictionary<NSAttributedStringKey,id> * */
/** @return NSDictionary<NSAttributedStringKey,id> * */
/** @return NSDictionary<NSAttributedStringKey,id> * */
/** @return NSArray<NSValue *> * */
/** @return NSArray<NSValue *> * */
/** @return NSArray<NSValue *> * */
/** @return id<NSTextViewDelegate> */
/** @return NSArray<NSString *> * */
fun NSTextView.smartDeleteRangeForProposedRange(proposedCharRange: NSRange): NSRange {
    val sel = ObjCRuntime.sel("smartDeleteRangeForProposedRange:")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, proposedCharRange) as NSRange
}

fun NSTextView.toggleSmartInsertDelete(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleSmartInsertDelete:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.smartInsertForString_replacingRange_beforeString_afterString(pasteString: MemorySegment, charRangeToReplace: NSRange, beforeString: MemorySegment, afterString: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("smartInsertForString:replacingRange:beforeString:afterString:")
    ObjCRuntime.msgSend(null, ptr, sel, pasteString, charRangeToReplace, beforeString, afterString)
}

fun NSTextView.smartInsertBeforeStringForString_replacingRange(pasteString: MemorySegment, charRangeToReplace: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("smartInsertBeforeStringForString:replacingRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pasteString, charRangeToReplace) as MemorySegment
}

fun NSTextView.smartInsertAfterStringForString_replacingRange(pasteString: MemorySegment, charRangeToReplace: NSRange): MemorySegment {
    val sel = ObjCRuntime.sel("smartInsertAfterStringForString:replacingRange:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pasteString, charRangeToReplace) as MemorySegment
}

fun NSTextView.toggleAutomaticQuoteSubstitution(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleAutomaticQuoteSubstitution:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.toggleAutomaticLinkDetection(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleAutomaticLinkDetection:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.toggleAutomaticDataDetection(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleAutomaticDataDetection:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.toggleAutomaticDashSubstitution(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleAutomaticDashSubstitution:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.toggleAutomaticTextReplacement(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleAutomaticTextReplacement:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.toggleAutomaticSpellingCorrection(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleAutomaticSpellingCorrection:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.checkTextInRange_types_options(range: NSRange, checkingTypes: NSTextCheckingTypes, options: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("checkTextInRange:types:options:")
    ObjCRuntime.msgSend(null, ptr, sel, range, checkingTypes, options)
}

fun NSTextView.handleTextCheckingResults_forRange_types_options_orthography_wordCount(results: MemorySegment, range: NSRange, checkingTypes: NSTextCheckingTypes, options: MemorySegment, orthography: MemorySegment, wordCount: NSInteger): Unit {
    val sel = ObjCRuntime.sel("handleTextCheckingResults:forRange:types:options:orthography:wordCount:")
    ObjCRuntime.msgSend(null, ptr, sel, results, range, checkingTypes, options, orthography, wordCount)
}

fun NSTextView.orderFrontSubstitutionsPanel(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("orderFrontSubstitutionsPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.checkTextInSelection(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("checkTextInSelection:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.checkTextInDocument(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("checkTextInDocument:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.smartInsertDeleteEnabled(): BOOL {
    val sel = ObjCRuntime.sel("smartInsertDeleteEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setSmartInsertDeleteEnabled(smartInsertDeleteEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setSmartInsertDeleteEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, smartInsertDeleteEnabled)
}

fun NSTextView.isAutomaticQuoteSubstitutionEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticQuoteSubstitutionEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAutomaticQuoteSubstitutionEnabled(automaticQuoteSubstitutionEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticQuoteSubstitutionEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticQuoteSubstitutionEnabled)
}

fun NSTextView.isAutomaticLinkDetectionEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticLinkDetectionEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAutomaticLinkDetectionEnabled(automaticLinkDetectionEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticLinkDetectionEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticLinkDetectionEnabled)
}

fun NSTextView.isAutomaticDataDetectionEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticDataDetectionEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAutomaticDataDetectionEnabled(automaticDataDetectionEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticDataDetectionEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticDataDetectionEnabled)
}

fun NSTextView.isAutomaticDashSubstitutionEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticDashSubstitutionEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAutomaticDashSubstitutionEnabled(automaticDashSubstitutionEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticDashSubstitutionEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticDashSubstitutionEnabled)
}

fun NSTextView.isAutomaticTextReplacementEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticTextReplacementEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAutomaticTextReplacementEnabled(automaticTextReplacementEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticTextReplacementEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticTextReplacementEnabled)
}

fun NSTextView.isAutomaticSpellingCorrectionEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticSpellingCorrectionEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAutomaticSpellingCorrectionEnabled(automaticSpellingCorrectionEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticSpellingCorrectionEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticSpellingCorrectionEnabled)
}

fun NSTextView.enabledTextCheckingTypes(): NSTextCheckingTypes {
    val sel = ObjCRuntime.sel("enabledTextCheckingTypes")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSTextCheckingTypes
}

fun NSTextView.setEnabledTextCheckingTypes(enabledTextCheckingTypes: NSTextCheckingTypes): Unit {
    val sel = ObjCRuntime.sel("setEnabledTextCheckingTypes:")
    ObjCRuntime.msgSend(null, ptr, sel, enabledTextCheckingTypes)
}

fun NSTextView.usesFindPanel(): BOOL {
    val sel = ObjCRuntime.sel("usesFindPanel")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setUsesFindPanel(usesFindPanel: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesFindPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, usesFindPanel)
}

fun NSTextView.usesFindBar(): BOOL {
    val sel = ObjCRuntime.sel("usesFindBar")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setUsesFindBar(usesFindBar: BOOL): Unit {
    val sel = ObjCRuntime.sel("setUsesFindBar:")
    ObjCRuntime.msgSend(null, ptr, sel, usesFindBar)
}

fun NSTextView.isIncrementalSearchingEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isIncrementalSearchingEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setIncrementalSearchingEnabled(incrementalSearchingEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setIncrementalSearchingEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, incrementalSearchingEnabled)
}

fun NSTextView.inlinePredictionType(): NSTextInputTraitType {
    val sel = ObjCRuntime.sel("inlinePredictionType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextInputTraitType
}

fun NSTextView.setInlinePredictionType(inlinePredictionType: NSTextInputTraitType): Unit {
    val sel = ObjCRuntime.sel("setInlinePredictionType:")
    ObjCRuntime.msgSend(null, ptr, sel, inlinePredictionType)
}

fun NSTextView.mathExpressionCompletionType(): NSTextInputTraitType {
    val sel = ObjCRuntime.sel("mathExpressionCompletionType")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextInputTraitType
}

fun NSTextView.setMathExpressionCompletionType(mathExpressionCompletionType: NSTextInputTraitType): Unit {
    val sel = ObjCRuntime.sel("setMathExpressionCompletionType:")
    ObjCRuntime.msgSend(null, ptr, sel, mathExpressionCompletionType)
}

// @property smartInsertDeleteEnabled
fun NSTextView.toggleQuickLookPreviewPanel(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleQuickLookPreviewPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

/** @return NSArray<id<QLPreviewItem>> * */
fun NSTextView.quickLookPreviewableItemsInRanges(ranges: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("quickLookPreviewableItemsInRanges:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ranges) as MemorySegment
}

fun NSTextView.updateQuickLookPreviewPanel(): Unit {
    val sel = ObjCRuntime.sel("updateQuickLookPreviewPanel")
    ObjCRuntime.msgSend(null, ptr, sel)
}

// ── Category: NSTextView_SharingService on NSTextView ─────────────────────────────────────────

fun NSTextView.orderFrontSharingServicePicker(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("orderFrontSharingServicePicker:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSTextView_TouchBar on NSTextView ─────────────────────────────────────────

fun NSTextView.toggleAutomaticTextCompletion(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleAutomaticTextCompletion:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSTextView.updateTouchBarItemIdentifiers(): Unit {
    val sel = ObjCRuntime.sel("updateTouchBarItemIdentifiers")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSTextView.updateTextTouchBarItems(): Unit {
    val sel = ObjCRuntime.sel("updateTextTouchBarItems")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSTextView.updateCandidates(): Unit {
    val sel = ObjCRuntime.sel("updateCandidates")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSTextView.isAutomaticTextCompletionEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticTextCompletionEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAutomaticTextCompletionEnabled(automaticTextCompletionEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticTextCompletionEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticTextCompletionEnabled)
}

fun NSTextView.allowsCharacterPickerTouchBarItem(): BOOL {
    val sel = ObjCRuntime.sel("allowsCharacterPickerTouchBarItem")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextView.setAllowsCharacterPickerTouchBarItem(allowsCharacterPickerTouchBarItem: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsCharacterPickerTouchBarItem:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsCharacterPickerTouchBarItem)
}

fun NSTextView.candidateListTouchBarItem(): MemorySegment {
    val sel = ObjCRuntime.sel("candidateListTouchBarItem")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property automaticTextCompletionEnabled
fun NSTextView_scrollableTextView(): MemorySegment {
    val sel = ObjCRuntime.sel("scrollableTextView")
    val cls = ObjCRuntime.getClass("NSTextView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSTextView fieldEditor]
fun NSTextView_fieldEditor(): MemorySegment {
    val sel = ObjCRuntime.sel("fieldEditor")
    val cls = ObjCRuntime.getClass("NSTextView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSTextView scrollableDocumentContentTextView]
fun NSTextView_scrollableDocumentContentTextView(): MemorySegment {
    val sel = ObjCRuntime.sel("scrollableDocumentContentTextView")
    val cls = ObjCRuntime.getClass("NSTextView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSTextView scrollablePlainDocumentContentTextView]
fun NSTextView_scrollablePlainDocumentContentTextView(): MemorySegment {
    val sel = ObjCRuntime.sel("scrollablePlainDocumentContentTextView")
    val cls = ObjCRuntime.getClass("NSTextView")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// ── Category: NSTextView_TextHighlight on NSTextView ─────────────────────────────────────────

fun NSTextView.drawTextHighlightBackgroundForTextRange_origin(textRange: MemorySegment, origin: NSPoint): Unit {
    val sel = ObjCRuntime.sel("drawTextHighlightBackgroundForTextRange:origin:")
    ObjCRuntime.msgSend(null, ptr, sel, textRange, origin)
}

fun NSTextView.highlight(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("highlight:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSTextView.textHighlightAttributes(): MemorySegment {
    val sel = ObjCRuntime.sel("textHighlightAttributes")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTextView.setTextHighlightAttributes(textHighlightAttributes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTextHighlightAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, textHighlightAttributes)
}

// @property textHighlightAttributes
/** @return NSDictionary<NSAttributedStringKey,id> * */
fun NSTextView.toggleBaseWritingDirection(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("toggleBaseWritingDirection:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}


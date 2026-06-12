package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextField
 * Superclass: NSControl
 * Protocols: NSUserInterfaceValidations, NSAccessibilityNavigableStaticText, NSTextContent
 */
open class NSTextField(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextField") }
        
    }
    
    fun selectText(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("selectText:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun textShouldBeginEditing(textObject: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("textShouldBeginEditing:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textObject) as BOOL
    }
    
    fun textShouldEndEditing(textObject: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("textShouldEndEditing:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, textObject) as BOOL
    }
    
    fun textDidBeginEditing(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("textDidBeginEditing:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
    }
    
    fun textDidEndEditing(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("textDidEndEditing:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
    }
    
    fun textDidChange(notification: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("textDidChange:")
        ObjCRuntime.msgSend(null, ptr, sel, notification)
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
    
    // @property backgroundColor
    fun backgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("backgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setBackgroundColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBackgroundColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property drawsBackground
    fun drawsBackground(): BOOL {
        val sel = ObjCRuntime.sel("drawsBackground")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setDrawsBackground(value: BOOL) {
        val sel = ObjCRuntime.sel("setDrawsBackground:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property textColor
    fun textColor(): MemorySegment {
        val sel = ObjCRuntime.sel("textColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTextColor(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTextColor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bordered
    fun isBordered(): BOOL {
        val sel = ObjCRuntime.sel("isBordered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBordered(value: BOOL) {
        val sel = ObjCRuntime.sel("setBordered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bezeled
    fun isBezeled(): BOOL {
        val sel = ObjCRuntime.sel("isBezeled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setBezeled(value: BOOL) {
        val sel = ObjCRuntime.sel("setBezeled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property editable
    fun isEditable(): BOOL {
        val sel = ObjCRuntime.sel("isEditable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEditable(value: BOOL) {
        val sel = ObjCRuntime.sel("setEditable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property selectable
    fun isSelectable(): BOOL {
        val sel = ObjCRuntime.sel("isSelectable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelectable(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelectable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSTextFieldDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property acceptsFirstResponder
    fun acceptsFirstResponder(): BOOL {
        val sel = ObjCRuntime.sel("acceptsFirstResponder")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property bezelStyle
    fun bezelStyle(): NSTextFieldBezelStyle {
        val sel = ObjCRuntime.sel("bezelStyle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextFieldBezelStyle
    }
    fun setBezelStyle(value: NSTextFieldBezelStyle) {
        val sel = ObjCRuntime.sel("setBezelStyle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property preferredMaxLayoutWidth
    fun preferredMaxLayoutWidth(): CGFloat {
        val sel = ObjCRuntime.sel("preferredMaxLayoutWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setPreferredMaxLayoutWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setPreferredMaxLayoutWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maximumNumberOfLines
    fun maximumNumberOfLines(): NSInteger {
        val sel = ObjCRuntime.sel("maximumNumberOfLines")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setMaximumNumberOfLines(value: NSInteger) {
        val sel = ObjCRuntime.sel("setMaximumNumberOfLines:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsDefaultTighteningForTruncation
    fun allowsDefaultTighteningForTruncation(): BOOL {
        val sel = ObjCRuntime.sel("allowsDefaultTighteningForTruncation")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsDefaultTighteningForTruncation(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsDefaultTighteningForTruncation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property lineBreakStrategy
    fun lineBreakStrategy(): NSLineBreakStrategy {
        val sel = ObjCRuntime.sel("lineBreakStrategy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSLineBreakStrategy
    }
    fun setLineBreakStrategy(value: NSLineBreakStrategy) {
        val sel = ObjCRuntime.sel("setLineBreakStrategy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsWritingTools
    fun allowsWritingTools(): BOOL {
        val sel = ObjCRuntime.sel("allowsWritingTools")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsWritingTools(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsWritingTools:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsWritingToolsAffordance
    fun allowsWritingToolsAffordance(): BOOL {
        val sel = ObjCRuntime.sel("allowsWritingToolsAffordance")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsWritingToolsAffordance(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsWritingToolsAffordance:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property placeholderStrings
    /** @return NSArray<NSString *> * */
    fun placeholderStrings(): MemorySegment {
        val sel = ObjCRuntime.sel("placeholderStrings")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPlaceholderStrings(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPlaceholderStrings:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property placeholderAttributedStrings
    /** @return NSArray<NSAttributedString *> * */
    fun placeholderAttributedStrings(): MemorySegment {
        val sel = ObjCRuntime.sel("placeholderAttributedStrings")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPlaceholderAttributedStrings(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPlaceholderAttributedStrings:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property resolvesNaturalAlignmentWithBaseWritingDirection
    fun resolvesNaturalAlignmentWithBaseWritingDirection(): BOOL {
        val sel = ObjCRuntime.sel("resolvesNaturalAlignmentWithBaseWritingDirection")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setResolvesNaturalAlignmentWithBaseWritingDirection(value: BOOL) {
        val sel = ObjCRuntime.sel("setResolvesNaturalAlignmentWithBaseWritingDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSTouchBar on NSTextField ─────────────────────────────────────────

fun NSTextField.isAutomaticTextCompletionEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isAutomaticTextCompletionEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextField.setAutomaticTextCompletionEnabled(automaticTextCompletionEnabled: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAutomaticTextCompletionEnabled:")
    ObjCRuntime.msgSend(null, ptr, sel, automaticTextCompletionEnabled)
}

fun NSTextField.allowsCharacterPickerTouchBarItem(): BOOL {
    val sel = ObjCRuntime.sel("allowsCharacterPickerTouchBarItem")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextField.setAllowsCharacterPickerTouchBarItem(allowsCharacterPickerTouchBarItem: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsCharacterPickerTouchBarItem:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsCharacterPickerTouchBarItem)
}

// @property automaticTextCompletionEnabled
fun NSTextField_labelWithString(stringValue: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("labelWithString:")
    val cls = ObjCRuntime.getClass("NSTextField")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, stringValue) as MemorySegment
}

// Class<*> method: +[NSTextField wrappingLabelWithString:]
fun NSTextField_wrappingLabelWithString(stringValue: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("wrappingLabelWithString:")
    val cls = ObjCRuntime.getClass("NSTextField")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, stringValue) as MemorySegment
}

// Class<*> method: +[NSTextField labelWithAttributedString:]
fun NSTextField_labelWithAttributedString(attributedStringValue: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("labelWithAttributedString:")
    val cls = ObjCRuntime.getClass("NSTextField")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, attributedStringValue) as MemorySegment
}

// Class<*> method: +[NSTextField textFieldWithString:]
fun NSTextField_textFieldWithString(stringValue: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("textFieldWithString:")
    val cls = ObjCRuntime.getClass("NSTextField")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, stringValue) as MemorySegment
}

// ── Category: NSTextFieldAttributedStringMethods on NSTextField ─────────────────────────────────────────

fun NSTextField.allowsEditingTextAttributes(): BOOL {
    val sel = ObjCRuntime.sel("allowsEditingTextAttributes")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextField.setAllowsEditingTextAttributes(allowsEditingTextAttributes: BOOL): Unit {
    val sel = ObjCRuntime.sel("setAllowsEditingTextAttributes:")
    ObjCRuntime.msgSend(null, ptr, sel, allowsEditingTextAttributes)
}

fun NSTextField.importsGraphics(): BOOL {
    val sel = ObjCRuntime.sel("importsGraphics")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTextField.setImportsGraphics(importsGraphics: BOOL): Unit {
    val sel = ObjCRuntime.sel("setImportsGraphics:")
    ObjCRuntime.msgSend(null, ptr, sel, importsGraphics)
}

// @property allowsEditingTextAttributes
fun NSTextField.setTitleWithMnemonic(stringWithAmpersand: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleWithMnemonic:")
    ObjCRuntime.msgSend(null, ptr, sel, stringWithAmpersand)
}


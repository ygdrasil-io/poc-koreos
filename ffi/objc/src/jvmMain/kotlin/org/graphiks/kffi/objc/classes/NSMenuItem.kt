package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSMenuItem
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding, NSValidatedUserInterfaceItem, NSUserInterfaceItemIdentification, NSAccessibilityElement, NSAccessibility
 */
open class NSMenuItem(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMenuItem") }
        
        open fun separatorItem(): MemorySegment {
            val sel = ObjCRuntime.sel("separatorItem")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun sectionHeaderWithTitle(title: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sectionHeaderWithTitle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, title) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun sectionHeaderWithTitle(title: String): MemorySegment = sectionHeaderWithTitle(ObjCRuntime.newNSString(Arena.global(), title))
        
        open fun usesUserKeyEquivalents(): BOOL {
            val sel = ObjCRuntime.sel("usesUserKeyEquivalents")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun setUsesUserKeyEquivalents(usesUserKeyEquivalents: BOOL): Unit {
            val sel = ObjCRuntime.sel("setUsesUserKeyEquivalents:")
            ObjCRuntime.msgSend(null, _class, sel, usesUserKeyEquivalents)
        }
        
        /** @return NSArray<NSMenuItem *> * */
        open fun writingToolsItems(): MemorySegment {
            val sel = ObjCRuntime.sel("writingToolsItems")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithTitle_action_keyEquivalent(string: MemorySegment, selector: MemorySegment, charCode: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTitle:action:keyEquivalent:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, string, selector, charCode) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithTitle_action_keyEquivalent(string: String, selector: MemorySegment, charCode: String): MemorySegment = initWithTitle_action_keyEquivalent(ObjCRuntime.newNSString(Arena.global(), string), selector, ObjCRuntime.newNSString(Arena.global(), charCode))
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    // @property usesUserKeyEquivalents
    open fun usesUserKeyEquivalents(): BOOL {
        val sel = ObjCRuntime.sel("usesUserKeyEquivalents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setUsesUserKeyEquivalents(value: BOOL) {
        val sel = ObjCRuntime.sel("setUsesUserKeyEquivalents:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property writingToolsItems
    /** @return NSArray<NSMenuItem *> * */
    open fun writingToolsItems(): MemorySegment {
        val sel = ObjCRuntime.sel("writingToolsItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property hasSubmenu
    open fun hasSubmenu(): BOOL {
        val sel = ObjCRuntime.sel("hasSubmenu")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property submenu
    open fun submenu(): MemorySegment {
        val sel = ObjCRuntime.sel("submenu")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setSubmenu(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubmenu:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property parentItem
    open fun parentItem(): MemorySegment {
        val sel = ObjCRuntime.sel("parentItem")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property attributedTitle
    open fun attributedTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("attributedTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setAttributedTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAttributedTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property subtitle
    open fun subtitle(): MemorySegment {
        val sel = ObjCRuntime.sel("subtitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setSubtitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubtitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun subtitleAsString(): String = ObjCRuntime.toJavaString(subtitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setSubtitle(value: String) = setSubtitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property separatorItem
    open fun isSeparatorItem(): BOOL {
        val sel = ObjCRuntime.sel("isSeparatorItem")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property sectionHeader
    open fun isSectionHeader(): BOOL {
        val sel = ObjCRuntime.sel("isSectionHeader")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property keyEquivalent
    open fun keyEquivalent(): MemorySegment {
        val sel = ObjCRuntime.sel("keyEquivalent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setKeyEquivalent(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setKeyEquivalent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun keyEquivalentAsString(): String = ObjCRuntime.toJavaString(keyEquivalent())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setKeyEquivalent(value: String) = setKeyEquivalent(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property keyEquivalentModifierMask
    open fun keyEquivalentModifierMask(): NSEventModifierFlags {
        val sel = ObjCRuntime.sel("keyEquivalentModifierMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSEventModifierFlags
    }
    open fun setKeyEquivalentModifierMask(value: NSEventModifierFlags) {
        val sel = ObjCRuntime.sel("setKeyEquivalentModifierMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property userKeyEquivalent
    open fun userKeyEquivalent(): MemorySegment {
        val sel = ObjCRuntime.sel("userKeyEquivalent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun userKeyEquivalentAsString(): String = ObjCRuntime.toJavaString(userKeyEquivalent())
    
    // @property allowsKeyEquivalentWhenHidden
    open fun allowsKeyEquivalentWhenHidden(): BOOL {
        val sel = ObjCRuntime.sel("allowsKeyEquivalentWhenHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsKeyEquivalentWhenHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsKeyEquivalentWhenHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsAutomaticKeyEquivalentLocalization
    open fun allowsAutomaticKeyEquivalentLocalization(): BOOL {
        val sel = ObjCRuntime.sel("allowsAutomaticKeyEquivalentLocalization")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsAutomaticKeyEquivalentLocalization(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsAutomaticKeyEquivalentLocalization:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsAutomaticKeyEquivalentMirroring
    open fun allowsAutomaticKeyEquivalentMirroring(): BOOL {
        val sel = ObjCRuntime.sel("allowsAutomaticKeyEquivalentMirroring")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAllowsAutomaticKeyEquivalentMirroring(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsAutomaticKeyEquivalentMirroring:")
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
    
    // @property state
    open fun state(): NSControlStateValue {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSControlStateValue
    }
    open fun setState(value: NSControlStateValue) {
        val sel = ObjCRuntime.sel("setState:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property onStateImage
    open fun onStateImage(): MemorySegment {
        val sel = ObjCRuntime.sel("onStateImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setOnStateImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setOnStateImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property offStateImage
    open fun offStateImage(): MemorySegment {
        val sel = ObjCRuntime.sel("offStateImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setOffStateImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setOffStateImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property mixedStateImage
    open fun mixedStateImage(): MemorySegment {
        val sel = ObjCRuntime.sel("mixedStateImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setMixedStateImage(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMixedStateImage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property alternate
    open fun isAlternate(): BOOL {
        val sel = ObjCRuntime.sel("isAlternate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAlternate(value: BOOL) {
        val sel = ObjCRuntime.sel("setAlternate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property indentationLevel
    open fun indentationLevel(): NSInteger {
        val sel = ObjCRuntime.sel("indentationLevel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    open fun setIndentationLevel(value: NSInteger) {
        val sel = ObjCRuntime.sel("setIndentationLevel:")
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
    
    // @property representedObject
    open fun representedObject(): MemorySegment {
        val sel = ObjCRuntime.sel("representedObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setRepresentedObject(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRepresentedObject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property view
    open fun view(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property highlighted
    open fun isHighlighted(): BOOL {
        val sel = ObjCRuntime.sel("isHighlighted")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property hidden
    open fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hiddenOrHasHiddenAncestor
    open fun isHiddenOrHasHiddenAncestor(): BOOL {
        val sel = ObjCRuntime.sel("isHiddenOrHasHiddenAncestor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property toolTip
    open fun toolTip(): MemorySegment {
        val sel = ObjCRuntime.sel("toolTip")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setToolTip(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setToolTip:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun toolTipAsString(): String = ObjCRuntime.toJavaString(toolTip())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setToolTip(value: String) = setToolTip(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property badge
    open fun badge(): MemorySegment {
        val sel = ObjCRuntime.sel("badge")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setBadge(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setBadge:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSMenuItem ─────────────────────────────────────────

fun NSMenuItem.setMnemonicLocation(location: NSUInteger): Unit {
    val sel = ObjCRuntime.sel("setMnemonicLocation:")
    ObjCRuntime.msgSend(null, ptr, sel, location)
}

fun NSMenuItem.mnemonicLocation(): NSUInteger {
    val sel = ObjCRuntime.sel("mnemonicLocation")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
}

fun NSMenuItem.mnemonic(): MemorySegment {
    val sel = ObjCRuntime.sel("mnemonic")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSMenuItem.setTitleWithMnemonic(stringWithAmpersand: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setTitleWithMnemonic:")
    ObjCRuntime.msgSend(null, ptr, sel, stringWithAmpersand)
}


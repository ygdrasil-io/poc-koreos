package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSFontManager
 * Superclass: NSObject
 * Protocols: NSMenuItemValidation
 */
open class NSFontManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSFontManager") }
        
        open fun setFontPanelFactory(factoryId: Class<*>): Unit {
            val sel = ObjCRuntime.sel("setFontPanelFactory:")
            ObjCRuntime.msgSend(null, _class, sel, factoryId)
        }
        
        open fun setFontManagerFactory(factoryId: Class<*>): Unit {
            val sel = ObjCRuntime.sel("setFontManagerFactory:")
            ObjCRuntime.msgSend(null, _class, sel, factoryId)
        }
        
        open fun sharedFontManager(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedFontManager")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun setSelectedFont_isMultiple(fontObj: MemorySegment, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setSelectedFont:isMultiple:")
        ObjCRuntime.msgSend(null, ptr, sel, fontObj, flag)
    }
    
    open fun setFontMenu(newMenu: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setFontMenu:")
        ObjCRuntime.msgSend(null, ptr, sel, newMenu)
    }
    
    open fun fontMenu(create: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("fontMenu:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, create) as MemorySegment
    }
    
    open fun fontPanel(create: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("fontPanel:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, create) as MemorySegment
    }
    
    open fun fontWithFamily_traits_weight_size(family: MemorySegment, traits: NSFontTraitMask, weight: NSInteger, size: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("fontWithFamily:traits:weight:size:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, family, traits, weight, size) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun fontWithFamily_traits_weight_size(family: String, traits: NSFontTraitMask, weight: NSInteger, size: CGFloat): MemorySegment = fontWithFamily_traits_weight_size(ObjCRuntime.newNSString(Arena.global(), family), traits, weight, size)
    
    open fun traitsOfFont(fontObj: MemorySegment): NSFontTraitMask {
        val sel = ObjCRuntime.sel("traitsOfFont:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontObj) as NSFontTraitMask
    }
    
    open fun weightOfFont(fontObj: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("weightOfFont:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, fontObj) as NSInteger
    }
    
    /** @return NSArray<NSArray *> * */
    open fun availableMembersOfFontFamily(fam: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("availableMembersOfFontFamily:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fam) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun availableMembersOfFontFamily(fam: String): MemorySegment = availableMembersOfFontFamily(ObjCRuntime.newNSString(Arena.global(), fam))
    
    open fun convertFont(fontObj: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("convertFont:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontObj) as MemorySegment
    }
    
    open fun convertFont_toSize(fontObj: MemorySegment, size: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("convertFont:toSize:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontObj, size) as MemorySegment
    }
    
    open fun convertFont_toFace(fontObj: MemorySegment, typeface: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("convertFont:toFace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontObj, typeface) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun convertFont_toFace(fontObj: MemorySegment, typeface: String): MemorySegment = convertFont_toFace(fontObj, ObjCRuntime.newNSString(Arena.global(), typeface))
    
    open fun convertFont_toFamily(fontObj: MemorySegment, family: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("convertFont:toFamily:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontObj, family) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun convertFont_toFamily(fontObj: MemorySegment, family: String): MemorySegment = convertFont_toFamily(fontObj, ObjCRuntime.newNSString(Arena.global(), family))
    
    open fun convertFont_toHaveTrait(fontObj: MemorySegment, trait: NSFontTraitMask): MemorySegment {
        val sel = ObjCRuntime.sel("convertFont:toHaveTrait:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontObj, trait) as MemorySegment
    }
    
    open fun convertFont_toNotHaveTrait(fontObj: MemorySegment, trait: NSFontTraitMask): MemorySegment {
        val sel = ObjCRuntime.sel("convertFont:toNotHaveTrait:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontObj, trait) as MemorySegment
    }
    
    open fun convertWeight_ofFont(upFlag: BOOL, fontObj: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("convertWeight:ofFont:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, upFlag, fontObj) as MemorySegment
    }
    
    open fun sendAction(): BOOL {
        val sel = ObjCRuntime.sel("sendAction")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun localizedNameForFamily_face(family: MemorySegment, faceKey: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("localizedNameForFamily:face:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, family, faceKey) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedNameForFamily_faceAsString(family: MemorySegment, faceKey: MemorySegment): String = ObjCRuntime.toJavaString(localizedNameForFamily_face(family, faceKey))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun localizedNameForFamily_face(family: String, faceKey: String): MemorySegment = localizedNameForFamily_face(ObjCRuntime.newNSString(Arena.global(), family), ObjCRuntime.newNSString(Arena.global(), faceKey))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    open fun localizedNameForFamily_faceAsString(family: String, faceKey: String): String = ObjCRuntime.toJavaString(localizedNameForFamily_face(ObjCRuntime.newNSString(Arena.global(), family), ObjCRuntime.newNSString(Arena.global(), faceKey)))
    
    open fun setSelectedAttributes_isMultiple(attributes: MemorySegment, flag: BOOL): Unit {
        val sel = ObjCRuntime.sel("setSelectedAttributes:isMultiple:")
        ObjCRuntime.msgSend(null, ptr, sel, attributes, flag)
    }
    
    /** @return NSDictionary<NSString *,id> * */
    open fun convertAttributes(attributes: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("convertAttributes:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attributes) as MemorySegment
    }
    
    open fun availableFontNamesMatchingFontDescriptor(descriptor: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("availableFontNamesMatchingFontDescriptor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, descriptor) as MemorySegment
    }
    
    open fun fontDescriptorsInCollection(collectionNames: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("fontDescriptorsInCollection:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, collectionNames) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun fontDescriptorsInCollection(collectionNames: String): MemorySegment = fontDescriptorsInCollection(ObjCRuntime.newNSString(Arena.global(), collectionNames))
    
    open fun addCollection_options(collectionName: MemorySegment, collectionOptions: NSFontCollectionOptions): BOOL {
        val sel = ObjCRuntime.sel("addCollection:options:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, collectionName, collectionOptions) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun addCollection_options(collectionName: String, collectionOptions: NSFontCollectionOptions): BOOL = addCollection_options(ObjCRuntime.newNSString(Arena.global(), collectionName), collectionOptions)
    
    open fun removeCollection(collectionName: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("removeCollection:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, collectionName) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun removeCollection(collectionName: String): BOOL = removeCollection(ObjCRuntime.newNSString(Arena.global(), collectionName))
    
    open fun addFontDescriptors_toCollection(descriptors: MemorySegment, collectionName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addFontDescriptors:toCollection:")
        ObjCRuntime.msgSend(null, ptr, sel, descriptors, collectionName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun addFontDescriptors_toCollection(descriptors: MemorySegment, collectionName: String): Unit = addFontDescriptors_toCollection(descriptors, ObjCRuntime.newNSString(Arena.global(), collectionName))
    
    open fun removeFontDescriptor_fromCollection(descriptor: MemorySegment, collection: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeFontDescriptor:fromCollection:")
        ObjCRuntime.msgSend(null, ptr, sel, descriptor, collection)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun removeFontDescriptor_fromCollection(descriptor: MemorySegment, collection: String): Unit = removeFontDescriptor_fromCollection(descriptor, ObjCRuntime.newNSString(Arena.global(), collection))
    
    open fun convertFontTraits(traits: NSFontTraitMask): NSFontTraitMask {
        val sel = ObjCRuntime.sel("convertFontTraits:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, traits) as NSFontTraitMask
    }
    
    // @property sharedFontManager
    open fun sharedFontManager(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedFontManager")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property multiple
    open fun isMultiple(): BOOL {
        val sel = ObjCRuntime.sel("isMultiple")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property selectedFont
    open fun selectedFont(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedFont")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property availableFonts
    /** @return NSArray<NSString *> * */
    open fun availableFonts(): MemorySegment {
        val sel = ObjCRuntime.sel("availableFonts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property availableFontFamilies
    /** @return NSArray<NSString *> * */
    open fun availableFontFamilies(): MemorySegment {
        val sel = ObjCRuntime.sel("availableFontFamilies")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
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
    
    // @property action
    open fun action(): MemorySegment {
        val sel = ObjCRuntime.sel("action")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setAction(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAction:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property collectionNames
    open fun collectionNames(): MemorySegment {
        val sel = ObjCRuntime.sel("collectionNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentFontAction
    open fun currentFontAction(): NSFontAction {
        val sel = ObjCRuntime.sel("currentFontAction")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSFontAction
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
    
}

// ── Category: NSFontManagerMenuActionMethods on NSFontManager ─────────────────────────────────────────

fun NSFontManager.fontNamed_hasTraits(fName: MemorySegment, someTraits: NSFontTraitMask): BOOL {
    val sel = ObjCRuntime.sel("fontNamed:hasTraits:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fName, someTraits) as BOOL
}

/** @return NSArray<NSString *> * */
fun NSFontManager.availableFontNamesWithTraits(someTraits: NSFontTraitMask): MemorySegment {
    val sel = ObjCRuntime.sel("availableFontNamesWithTraits:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, someTraits) as MemorySegment
}

fun NSFontManager.addFontTrait(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addFontTrait:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSFontManager.removeFontTrait(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeFontTrait:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSFontManager.modifyFontViaPanel(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("modifyFontViaPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSFontManager.modifyFont(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("modifyFont:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSFontManager.orderFrontFontPanel(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("orderFrontFontPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSFontManager.orderFrontStylesPanel(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("orderFrontStylesPanel:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}


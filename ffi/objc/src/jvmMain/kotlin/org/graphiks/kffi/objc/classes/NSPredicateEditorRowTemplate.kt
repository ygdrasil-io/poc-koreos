package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPredicateEditorRowTemplate
 * Superclass: NSObject
 * Protocols: NSCoding, NSCopying
 */
open class NSPredicateEditorRowTemplate(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPredicateEditorRowTemplate") }
        
        /** @return NSArray<NSPredicateEditorRowTemplate *> * */
        open fun templatesWithAttributeKeyPaths_inEntityDescription(keyPaths: MemorySegment, entityDescription: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("templatesWithAttributeKeyPaths:inEntityDescription:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, keyPaths, entityDescription) as MemorySegment
        }
        
    }
    
    open fun matchForPredicate(predicate: MemorySegment): Double {
        val sel = ObjCRuntime.sel("matchForPredicate:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, predicate) as Double
    }
    
    open fun setPredicate(predicate: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setPredicate:")
        ObjCRuntime.msgSend(null, ptr, sel, predicate)
    }
    
    open fun predicateWithSubpredicates(subpredicates: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("predicateWithSubpredicates:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, subpredicates) as MemorySegment
    }
    
    /** @return NSArray<NSPredicate *> * */
    open fun displayableSubpredicatesOfPredicate(predicate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("displayableSubpredicatesOfPredicate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, predicate) as MemorySegment
    }
    
    open fun initWithLeftExpressions_rightExpressions_modifier_operators_options(leftExpressions: MemorySegment, rightExpressions: MemorySegment, modifier: NSComparisonPredicateModifier, operators: MemorySegment, options: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLeftExpressions:rightExpressions:modifier:operators:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, leftExpressions, rightExpressions, modifier, operators, options) as MemorySegment
    }
    
    open fun initWithLeftExpressions_rightExpressionAttributeType_modifier_operators_options(leftExpressions: MemorySegment, attributeType: NSAttributeType, modifier: NSComparisonPredicateModifier, operators: MemorySegment, options: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithLeftExpressions:rightExpressionAttributeType:modifier:operators:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, leftExpressions, attributeType, modifier, operators, options) as MemorySegment
    }
    
    open fun initWithCompoundTypes(compoundTypes: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCompoundTypes:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, compoundTypes) as MemorySegment
    }
    
    // @property templateViews
    /** @return NSArray<NSView *> * */
    open fun templateViews(): MemorySegment {
        val sel = ObjCRuntime.sel("templateViews")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property leftExpressions
    /** @return NSArray<NSExpression *> * */
    open fun leftExpressions(): MemorySegment {
        val sel = ObjCRuntime.sel("leftExpressions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property rightExpressions
    /** @return NSArray<NSExpression *> * */
    open fun rightExpressions(): MemorySegment {
        val sel = ObjCRuntime.sel("rightExpressions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property rightExpressionAttributeType
    open fun rightExpressionAttributeType(): NSAttributeType {
        val sel = ObjCRuntime.sel("rightExpressionAttributeType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSAttributeType
    }
    
    // @property modifier
    open fun modifier(): NSComparisonPredicateModifier {
        val sel = ObjCRuntime.sel("modifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSComparisonPredicateModifier
    }
    
    // @property operators
    /** @return NSArray<NSNumber *> * */
    open fun operators(): MemorySegment {
        val sel = ObjCRuntime.sel("operators")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property options
    open fun options(): NSUInteger {
        val sel = ObjCRuntime.sel("options")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property compoundTypes
    /** @return NSArray<NSNumber *> * */
    open fun compoundTypes(): MemorySegment {
        val sel = ObjCRuntime.sel("compoundTypes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


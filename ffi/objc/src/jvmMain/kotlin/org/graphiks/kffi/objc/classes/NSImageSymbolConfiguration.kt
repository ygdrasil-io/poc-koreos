package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSImageSymbolConfiguration
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSImageSymbolConfiguration(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSImageSymbolConfiguration") }
        
        open fun configurationWithPointSize_weight_scale(pointSize: CGFloat, weight: NSFontWeight, scale: NSImageSymbolScale): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithPointSize:weight:scale:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, pointSize, weight, scale) as MemorySegment
        }
        
        open fun configurationWithPointSize_weight(pointSize: CGFloat, weight: NSFontWeight): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithPointSize:weight:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, pointSize, weight) as MemorySegment
        }
        
        open fun configurationWithTextStyle_scale(style: NSFontTextStyle, scale: NSImageSymbolScale): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithTextStyle:scale:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, style, scale) as MemorySegment
        }
        
        open fun configurationWithTextStyle(style: NSFontTextStyle): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithTextStyle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, style) as MemorySegment
        }
        
        open fun configurationWithScale(scale: NSImageSymbolScale): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithScale:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, scale) as MemorySegment
        }
        
        open fun configurationPreferringMonochrome(): MemorySegment {
            val sel = ObjCRuntime.sel("configurationPreferringMonochrome")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun configurationPreferringHierarchical(): MemorySegment {
            val sel = ObjCRuntime.sel("configurationPreferringHierarchical")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun configurationWithHierarchicalColor(hierarchicalColor: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithHierarchicalColor:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, hierarchicalColor) as MemorySegment
        }
        
        open fun configurationWithPaletteColors(paletteColors: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithPaletteColors:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, paletteColors) as MemorySegment
        }
        
        open fun configurationPreferringMulticolor(): MemorySegment {
            val sel = ObjCRuntime.sel("configurationPreferringMulticolor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun configurationWithVariableValueMode(variableValueMode: NSImageSymbolVariableValueMode): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithVariableValueMode:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, variableValueMode) as MemorySegment
        }
        
        open fun configurationWithColorRenderingMode(mode: NSImageSymbolColorRenderingMode): MemorySegment {
            val sel = ObjCRuntime.sel("configurationWithColorRenderingMode:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, mode) as MemorySegment
        }
        
    }
    
    open fun configurationByApplyingConfiguration(configuration: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("configurationByApplyingConfiguration:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, configuration) as MemorySegment
    }
    
}


package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSGraphicsContext
 * Superclass: NSObject
 */
open class NSGraphicsContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGraphicsContext") }
        
        open fun graphicsContextWithAttributes(attributes: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("graphicsContextWithAttributes:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, attributes) as MemorySegment
        }
        
        open fun graphicsContextWithBitmapImageRep(bitmapRep: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("graphicsContextWithBitmapImageRep:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, bitmapRep) as MemorySegment
        }
        
        open fun graphicsContextWithCGContext_flipped(graphicsPort: MemorySegment, initialFlippedState: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("graphicsContextWithCGContext:flipped:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, graphicsPort, initialFlippedState) as MemorySegment
        }
        
        open fun currentContextDrawingToScreen(): BOOL {
            val sel = ObjCRuntime.sel("currentContextDrawingToScreen")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        open fun saveGraphicsState(): Unit {
            val sel = ObjCRuntime.sel("saveGraphicsState")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        open fun restoreGraphicsState(): Unit {
            val sel = ObjCRuntime.sel("restoreGraphicsState")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        open fun currentContext(): MemorySegment {
            val sel = ObjCRuntime.sel("currentContext")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun setCurrentContext(currentContext: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setCurrentContext:")
            ObjCRuntime.msgSend(null, _class, sel, currentContext)
        }
        
    }
    
    }
    
    }
    
    open fun flushGraphics(): Unit {
        val sel = ObjCRuntime.sel("flushGraphics")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property currentContext
    }
    }
    
    // @property attributes
    /** @return NSDictionary<NSGraphicsContextAttributeKey,id> * */
    open fun attributes(): MemorySegment {
        val sel = ObjCRuntime.sel("attributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property drawingToScreen
    open fun isDrawingToScreen(): BOOL {
        val sel = ObjCRuntime.sel("isDrawingToScreen")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property CGContext
    open fun CGContext(): MemorySegment {
        val sel = ObjCRuntime.sel("CGContext")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property flipped
    open fun isFlipped(): BOOL {
        val sel = ObjCRuntime.sel("isFlipped")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSGraphicsContext_RenderingOptions on NSGraphicsContext ─────────────────────────────────────────

fun NSGraphicsContext.shouldAntialias(): BOOL {
    val sel = ObjCRuntime.sel("shouldAntialias")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSGraphicsContext.setShouldAntialias(shouldAntialias: BOOL): Unit {
    val sel = ObjCRuntime.sel("setShouldAntialias:")
    ObjCRuntime.msgSend(null, ptr, sel, shouldAntialias)
}

fun NSGraphicsContext.imageInterpolation(): NSImageInterpolation {
    val sel = ObjCRuntime.sel("imageInterpolation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageInterpolation
}

fun NSGraphicsContext.setImageInterpolation(imageInterpolation: NSImageInterpolation): Unit {
    val sel = ObjCRuntime.sel("setImageInterpolation:")
    ObjCRuntime.msgSend(null, ptr, sel, imageInterpolation)
}

fun NSGraphicsContext.patternPhase(): NSPoint {
    val sel = ObjCRuntime.sel("patternPhase")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
}

fun NSGraphicsContext.setPatternPhase(patternPhase: NSPoint): Unit {
    val sel = ObjCRuntime.sel("setPatternPhase:")
    ObjCRuntime.msgSend(null, ptr, sel, patternPhase)
}

fun NSGraphicsContext.compositingOperation(): NSCompositingOperation {
    val sel = ObjCRuntime.sel("compositingOperation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCompositingOperation
}

fun NSGraphicsContext.setCompositingOperation(compositingOperation: NSCompositingOperation): Unit {
    val sel = ObjCRuntime.sel("setCompositingOperation:")
    ObjCRuntime.msgSend(null, ptr, sel, compositingOperation)
}

fun NSGraphicsContext.colorRenderingIntent(): NSColorRenderingIntent {
    val sel = ObjCRuntime.sel("colorRenderingIntent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorRenderingIntent
}

fun NSGraphicsContext.setColorRenderingIntent(colorRenderingIntent: NSColorRenderingIntent): Unit {
    val sel = ObjCRuntime.sel("setColorRenderingIntent:")
    ObjCRuntime.msgSend(null, ptr, sel, colorRenderingIntent)
}

// @property shouldAntialias
    val sel = ObjCRuntime.sel("shouldAntialias")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}
    val sel = ObjCRuntime.sel("setShouldAntialias:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property imageInterpolation
    val sel = ObjCRuntime.sel("imageInterpolation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageInterpolation
}
    val sel = ObjCRuntime.sel("setImageInterpolation:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property patternPhase
    val sel = ObjCRuntime.sel("patternPhase")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
}
    val sel = ObjCRuntime.sel("setPatternPhase:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property compositingOperation
    val sel = ObjCRuntime.sel("compositingOperation")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSCompositingOperation
}
    val sel = ObjCRuntime.sel("setCompositingOperation:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property colorRenderingIntent
    val sel = ObjCRuntime.sel("colorRenderingIntent")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorRenderingIntent
}
    val sel = ObjCRuntime.sel("setColorRenderingIntent:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSQuartzCoreAdditions on NSGraphicsContext ─────────────────────────────────────────

fun NSGraphicsContext.CIContext(): MemorySegment {
    val sel = ObjCRuntime.sel("CIContext")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property CIContext
    val sel = ObjCRuntime.sel("CIContext")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSGraphicsContextDeprecated on NSGraphicsContext ─────────────────────────────────────────

fun NSGraphicsContext.focusStack(): MemorySegment {
    val sel = ObjCRuntime.sel("focusStack")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSGraphicsContext.setFocusStack(stack: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setFocusStack:")
    ObjCRuntime.msgSend(null, ptr, sel, stack)
}

fun NSGraphicsContext.graphicsPort(): MemorySegment {
    val sel = ObjCRuntime.sel("graphicsPort")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class<*> method: +[NSGraphicsContext setGraphicsState:]
fun NSGraphicsContext_setGraphicsState(gState: NSInteger): Unit {
    val sel = ObjCRuntime.sel("setGraphicsState:")
    val cls = ObjCRuntime.getClass("NSGraphicsContext")
    ObjCRuntime.msgSend(null, cls, sel, gState)
}

// Class<*> method: +[NSGraphicsContext graphicsContextWithGraphicsPort:flipped:]
fun NSGraphicsContext_graphicsContextWithGraphicsPort_flipped(graphicsPort: MemorySegment, initialFlippedState: BOOL): MemorySegment {
    val sel = ObjCRuntime.sel("graphicsContextWithGraphicsPort:flipped:")
    val cls = ObjCRuntime.getClass("NSGraphicsContext")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, graphicsPort, initialFlippedState) as MemorySegment
}

// Class<*> method: +[NSGraphicsContext graphicsContextWithWindow:]
fun NSGraphicsContext_graphicsContextWithWindow(window: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("graphicsContextWithWindow:")
    val cls = ObjCRuntime.getClass("NSGraphicsContext")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, window) as MemorySegment
}

// @property graphicsPort
    val sel = ObjCRuntime.sel("graphicsPort")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}


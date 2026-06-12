package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSWritingToolsCoordinator
 * Superclass: NSObject
 */
open class NSWritingToolsCoordinator(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWritingToolsCoordinator") }
        
        open fun isWritingToolsAvailable(): BOOL {
            val sel = ObjCRuntime.sel("isWritingToolsAvailable")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    open fun initWithDelegate(delegate: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDelegate:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, delegate) as MemorySegment
    }
    
    open fun stopWritingTools(): Unit {
        val sel = ObjCRuntime.sel("stopWritingTools")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun updateRange_withText_reason_forContextWithIdentifier(range: NSRange, replacementText: MemorySegment, reason: NSWritingToolsCoordinatorTextUpdateReason, contextID: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("updateRange:withText:reason:forContextWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), replacementText, reason, contextID)
    }
    
    open fun updateForReflowedTextInContextWithIdentifier(contextID: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("updateForReflowedTextInContextWithIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, contextID)
    }
    
    // @property isWritingToolsAvailable
    open fun isWritingToolsAvailable(): BOOL {
        val sel = ObjCRuntime.sel("isWritingToolsAvailable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property delegate
    /** @return id<NSWritingToolsCoordinatorDelegate> */
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property view
    open fun view(): MemorySegment {
        val sel = ObjCRuntime.sel("view")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property effectContainerView
    open fun effectContainerView(): MemorySegment {
        val sel = ObjCRuntime.sel("effectContainerView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setEffectContainerView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEffectContainerView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property decorationContainerView
    open fun decorationContainerView(): MemorySegment {
        val sel = ObjCRuntime.sel("decorationContainerView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDecorationContainerView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDecorationContainerView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property state
    open fun state(): NSWritingToolsCoordinatorState {
        val sel = ObjCRuntime.sel("state")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingToolsCoordinatorState
    }
    
    // @property preferredBehavior
    open fun preferredBehavior(): NSWritingToolsBehavior {
        val sel = ObjCRuntime.sel("preferredBehavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingToolsBehavior
    }
    open fun setPreferredBehavior(value: NSWritingToolsBehavior) {
        val sel = ObjCRuntime.sel("setPreferredBehavior:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property behavior
    open fun behavior(): NSWritingToolsBehavior {
        val sel = ObjCRuntime.sel("behavior")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingToolsBehavior
    }
    
    // @property preferredResultOptions
    open fun preferredResultOptions(): NSWritingToolsResultOptions {
        val sel = ObjCRuntime.sel("preferredResultOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingToolsResultOptions
    }
    open fun setPreferredResultOptions(value: NSWritingToolsResultOptions) {
        val sel = ObjCRuntime.sel("setPreferredResultOptions:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property resultOptions
    open fun resultOptions(): NSWritingToolsResultOptions {
        val sel = ObjCRuntime.sel("resultOptions")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSWritingToolsResultOptions
    }
    
    // @property includesTextListMarkers
    open fun includesTextListMarkers(): BOOL {
        val sel = ObjCRuntime.sel("includesTextListMarkers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setIncludesTextListMarkers(value: BOOL) {
        val sel = ObjCRuntime.sel("setIncludesTextListMarkers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


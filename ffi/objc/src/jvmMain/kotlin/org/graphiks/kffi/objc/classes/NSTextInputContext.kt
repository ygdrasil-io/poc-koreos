package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTextInputContext
 * Superclass: NSObject
 */
open class NSTextInputContext(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTextInputContext") }
        
        open fun localizedNameForInputSource(inputSourceIdentifier: NSTextInputSourceIdentifier): MemorySegment {
            val sel = ObjCRuntime.sel("localizedNameForInputSource:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, inputSourceIdentifier) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        open fun localizedNameForInputSourceAsString(inputSourceIdentifier: NSTextInputSourceIdentifier): String = ObjCRuntime.toJavaString(localizedNameForInputSource(inputSourceIdentifier))
        
        open fun currentInputContext(): MemorySegment {
            val sel = ObjCRuntime.sel("currentInputContext")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithClient(client: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithClient:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, client) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun activate(): Unit {
        val sel = ObjCRuntime.sel("activate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun deactivate(): Unit {
        val sel = ObjCRuntime.sel("deactivate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun handleEvent(event: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("handleEvent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, event) as BOOL
    }
    
    open fun discardMarkedText(): Unit {
        val sel = ObjCRuntime.sel("discardMarkedText")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun invalidateCharacterCoordinates(): Unit {
        val sel = ObjCRuntime.sel("invalidateCharacterCoordinates")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun textInputClientWillStartScrollingOrZooming(): Unit {
        val sel = ObjCRuntime.sel("textInputClientWillStartScrollingOrZooming")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun textInputClientDidEndScrollingOrZooming(): Unit {
        val sel = ObjCRuntime.sel("textInputClientDidEndScrollingOrZooming")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun textInputClientDidUpdateSelection(): Unit {
        val sel = ObjCRuntime.sel("textInputClientDidUpdateSelection")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun textInputClientDidScroll(): Unit {
        val sel = ObjCRuntime.sel("textInputClientDidScroll")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property currentInputContext
    }
    
    // @property client
    /** @return id<NSTextInputClient> */
    open fun client(): MemorySegment {
        val sel = ObjCRuntime.sel("client")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property acceptsGlyphInfo
    open fun acceptsGlyphInfo(): BOOL {
        val sel = ObjCRuntime.sel("acceptsGlyphInfo")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setAcceptsGlyphInfo(value: BOOL) {
        val sel = ObjCRuntime.sel("setAcceptsGlyphInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowedInputSourceLocales
    /** @return NSArray<NSString *> * */
    open fun allowedInputSourceLocales(): MemorySegment {
        val sel = ObjCRuntime.sel("allowedInputSourceLocales")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setAllowedInputSourceLocales(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAllowedInputSourceLocales:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property keyboardInputSources
    /** @return NSArray<NSTextInputSourceIdentifier> * */
    open fun keyboardInputSources(): MemorySegment {
        val sel = ObjCRuntime.sel("keyboardInputSources")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedKeyboardInputSource
    open fun selectedKeyboardInputSource(): NSTextInputSourceIdentifier {
        val sel = ObjCRuntime.sel("selectedKeyboardInputSource")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTextInputSourceIdentifier
    }
    open fun setSelectedKeyboardInputSource(value: NSTextInputSourceIdentifier) {
        val sel = ObjCRuntime.sel("setSelectedKeyboardInputSource:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


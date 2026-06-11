/**
 * Kotlin/JVM wrapper for Objective-C class: NSGlyphGenerator
 * Superclass: NSObject
 */
open class NSGlyphGenerator(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSGlyphGenerator") }
        
        fun sharedGlyphGenerator(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedGlyphGenerator")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun generateGlyphsForGlyphStorage_desiredNumberOfCharacters_glyphIndex_characterIndex(glyphStorage: MemorySegment, nChars: NSUInteger, glyphIndex: MemorySegment, charIndex: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("generateGlyphsForGlyphStorage:desiredNumberOfCharacters:glyphIndex:characterIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, glyphStorage, nChars, glyphIndex, charIndex)
    }
    
    // @property sharedGlyphGenerator
    fun sharedGlyphGenerator(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedGlyphGenerator")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


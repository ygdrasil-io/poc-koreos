/**
 * Kotlin/JVM wrapper for Objective-C class: NSOpenGLPixelFormat
 * Superclass: NSObject
 * Protocols: NSCoding
 */
open class NSOpenGLPixelFormat(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSOpenGLPixelFormat") }
        
    }
    
    fun initWithCGLPixelFormatObj(format: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGLPixelFormatObj:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, format) as MemorySegment
    }
    
    fun initWithAttributes(attribs: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithAttributes:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attribs) as MemorySegment
    }
    
    fun initWithData(attribs: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attribs) as MemorySegment
    }
    
    fun attributes(): MemorySegment {
        val sel = ObjCRuntime.sel("attributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun setAttributes(attribs: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setAttributes:")
        ObjCRuntime.msgSend(null, ptr, sel, attribs)
    }
    
    fun getValues_forAttribute_forVirtualScreen(vals: MemorySegment, attrib: NSOpenGLPixelFormatAttribute, screen: GLint): Unit {
        val sel = ObjCRuntime.sel("getValues:forAttribute:forVirtualScreen:")
        ObjCRuntime.msgSend(null, ptr, sel, vals, attrib, screen)
    }
    
    // @property numberOfVirtualScreens
    fun numberOfVirtualScreens(): GLint {
        val sel = ObjCRuntime.sel("numberOfVirtualScreens")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as GLint
    }
    
    // @property CGLPixelFormatObj
    fun CGLPixelFormatObj(): MemorySegment {
        val sel = ObjCRuntime.sel("CGLPixelFormatObj")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


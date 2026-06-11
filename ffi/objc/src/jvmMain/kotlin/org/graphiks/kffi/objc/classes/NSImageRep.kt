/**
 * Kotlin/JVM wrapper for Objective-C class: NSImageRep
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSImageRep(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSImageRep") }
        
        fun registerImageRepClass(imageRepClass: Class): Unit {
            val sel = ObjCRuntime.sel("registerImageRepClass:")
            ObjCRuntime.msgSend(null, _class, sel, imageRepClass)
        }
        
        fun unregisterImageRepClass(imageRepClass: Class): Unit {
            val sel = ObjCRuntime.sel("unregisterImageRepClass:")
            ObjCRuntime.msgSend(null, _class, sel, imageRepClass)
        }
        
        fun imageRepClassForFileType(type: MemorySegment): Class {
            val sel = ObjCRuntime.sel("imageRepClassForFileType:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, type) as Class
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun imageRepClassForFileType(type: String): Class = imageRepClassForFileType(ObjCRuntime.newNSString(Arena.global(), type))
        
        fun imageRepClassForPasteboardType(type: NSPasteboardType): Class {
            val sel = ObjCRuntime.sel("imageRepClassForPasteboardType:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, type) as Class
        }
        
        fun imageRepClassForType(type: MemorySegment): Class {
            val sel = ObjCRuntime.sel("imageRepClassForType:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, type) as Class
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun imageRepClassForType(type: String): Class = imageRepClassForType(ObjCRuntime.newNSString(Arena.global(), type))
        
        fun imageRepClassForData(`data`: MemorySegment): Class {
            val sel = ObjCRuntime.sel("imageRepClassForData:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`) as Class
        }
        
        fun canInitWithData(`data`: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("canInitWithData:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, `data`) as BOOL
        }
        
        /** @return NSArray<NSString *> * */
        fun imageUnfilteredFileTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("imageUnfilteredFileTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSPasteboardType> * */
        fun imageUnfilteredPasteboardTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("imageUnfilteredPasteboardTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSString *> * */
        fun imageFileTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("imageFileTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSPasteboardType> * */
        fun imagePasteboardTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("imagePasteboardTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun canInitWithPasteboard(pasteboard: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("canInitWithPasteboard:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, pasteboard) as BOOL
        }
        
        /** @return NSArray<NSImageRep *> * */
        fun imageRepsWithContentsOfFile(filename: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepsWithContentsOfFile:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, filename) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun imageRepsWithContentsOfFile(filename: String): MemorySegment = imageRepsWithContentsOfFile(ObjCRuntime.newNSString(Arena.global(), filename))
        
        fun imageRepWithContentsOfFile(filename: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepWithContentsOfFile:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, filename) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun imageRepWithContentsOfFile(filename: String): MemorySegment = imageRepWithContentsOfFile(ObjCRuntime.newNSString(Arena.global(), filename))
        
        /** @return NSArray<NSImageRep *> * */
        fun imageRepsWithContentsOfURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepsWithContentsOfURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        fun imageRepWithContentsOfURL(url: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepWithContentsOfURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url) as MemorySegment
        }
        
        /** @return NSArray<NSImageRep *> * */
        fun imageRepsWithPasteboard(pasteboard: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepsWithPasteboard:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, pasteboard) as MemorySegment
        }
        
        fun imageRepWithPasteboard(pasteboard: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepWithPasteboard:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, pasteboard) as MemorySegment
        }
        
        /** @return NSArray<Class> * */
        fun registeredImageRepClasses(): MemorySegment {
            val sel = ObjCRuntime.sel("registeredImageRepClasses")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSString *> * */
        fun imageUnfilteredTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("imageUnfilteredTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSString *> * */
        fun imageTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("imageTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun draw(): BOOL {
        val sel = ObjCRuntime.sel("draw")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun drawAtPoint(point: NSPoint): BOOL {
        val sel = ObjCRuntime.sel("drawAtPoint:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(point, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as BOOL
    }
    
    fun drawInRect(rect: NSRect): BOOL {
        val sel = ObjCRuntime.sel("drawInRect:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"))) as BOOL
    }
    
    fun drawInRect_fromRect_operation_fraction_respectFlipped_hints(dstSpacePortionRect: NSRect, srcSpacePortionRect: NSRect, op: NSCompositingOperation, requestedAlpha: CGFloat, respectContextIsFlipped: BOOL, hints: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("drawInRect:fromRect:operation:fraction:respectFlipped:hints:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(dstSpacePortionRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), ObjCRuntime.ObjCStructArg(srcSpacePortionRect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), op, requestedAlpha, respectContextIsFlipped, hints) as BOOL
    }
    
    fun CGImageForProposedRect_context_hints(proposedDestRect: MemorySegment, context: MemorySegment, hints: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("CGImageForProposedRect:context:hints:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, proposedDestRect, context, hints) as MemorySegment
    }
    
    // @property size
    fun size(): NSSize {
        val sel = ObjCRuntime.sel("size")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property alpha
    fun hasAlpha(): BOOL {
        val sel = ObjCRuntime.sel("hasAlpha")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAlpha(value: BOOL) {
        val sel = ObjCRuntime.sel("setAlpha:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property opaque
    fun isOpaque(): BOOL {
        val sel = ObjCRuntime.sel("isOpaque")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setOpaque(value: BOOL) {
        val sel = ObjCRuntime.sel("setOpaque:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property colorSpaceName
    fun colorSpaceName(): NSColorSpaceName {
        val sel = ObjCRuntime.sel("colorSpaceName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorSpaceName
    }
    fun setColorSpaceName(value: NSColorSpaceName) {
        val sel = ObjCRuntime.sel("setColorSpaceName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bitsPerSample
    fun bitsPerSample(): NSInteger {
        val sel = ObjCRuntime.sel("bitsPerSample")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setBitsPerSample(value: NSInteger) {
        val sel = ObjCRuntime.sel("setBitsPerSample:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pixelsWide
    fun pixelsWide(): NSInteger {
        val sel = ObjCRuntime.sel("pixelsWide")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setPixelsWide(value: NSInteger) {
        val sel = ObjCRuntime.sel("setPixelsWide:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pixelsHigh
    fun pixelsHigh(): NSInteger {
        val sel = ObjCRuntime.sel("pixelsHigh")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setPixelsHigh(value: NSInteger) {
        val sel = ObjCRuntime.sel("setPixelsHigh:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property layoutDirection
    fun layoutDirection(): NSImageLayoutDirection {
        val sel = ObjCRuntime.sel("layoutDirection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSImageLayoutDirection
    }
    fun setLayoutDirection(value: NSImageLayoutDirection) {
        val sel = ObjCRuntime.sel("setLayoutDirection:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property registeredImageRepClasses
    /** @return NSArray<Class> * */
    fun registeredImageRepClasses(): MemorySegment {
        val sel = ObjCRuntime.sel("registeredImageRepClasses")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property imageUnfilteredTypes
    /** @return NSArray<NSString *> * */
    fun imageUnfilteredTypes(): MemorySegment {
        val sel = ObjCRuntime.sel("imageUnfilteredTypes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property imageTypes
    /** @return NSArray<NSString *> * */
    fun imageTypes(): MemorySegment {
        val sel = ObjCRuntime.sel("imageTypes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


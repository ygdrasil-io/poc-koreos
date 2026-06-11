/**
 * Kotlin/JVM wrapper for Objective-C class: NSPDFImageRep
 * Superclass: NSImageRep
 */
open class NSPDFImageRep(ptr: MemorySegment) : NSImageRep(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPDFImageRep") }
        
        fun imageRepWithData(pdfData: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("imageRepWithData:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, pdfData) as MemorySegment
        }
        
    }
    
    fun initWithData(pdfData: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pdfData) as MemorySegment
    }
    
    // @property PDFRepresentation
    fun PDFRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("PDFRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bounds
    fun bounds(): NSRect {
        val sel = ObjCRuntime.sel("bounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property currentPage
    fun currentPage(): NSInteger {
        val sel = ObjCRuntime.sel("currentPage")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    fun setCurrentPage(value: NSInteger) {
        val sel = ObjCRuntime.sel("setCurrentPage:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pageCount
    fun pageCount(): NSInteger {
        val sel = ObjCRuntime.sel("pageCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
}


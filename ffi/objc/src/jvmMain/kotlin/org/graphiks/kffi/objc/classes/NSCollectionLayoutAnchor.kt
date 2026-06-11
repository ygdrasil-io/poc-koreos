/**
 * Kotlin/JVM wrapper for Objective-C class: NSCollectionLayoutAnchor
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSCollectionLayoutAnchor(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSCollectionLayoutAnchor") }
        
        fun layoutAnchorWithEdges(edges: NSDirectionalRectEdge): MemorySegment {
            val sel = ObjCRuntime.sel("layoutAnchorWithEdges:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, edges) as MemorySegment
        }
        
        fun layoutAnchorWithEdges_absoluteOffset(edges: NSDirectionalRectEdge, absoluteOffset: NSPoint): MemorySegment {
            val sel = ObjCRuntime.sel("layoutAnchorWithEdges:absoluteOffset:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, edges, ObjCRuntime.ObjCStructArg(absoluteOffset, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
        }
        
        fun layoutAnchorWithEdges_fractionalOffset(edges: NSDirectionalRectEdge, fractionalOffset: NSPoint): MemorySegment {
            val sel = ObjCRuntime.sel("layoutAnchorWithEdges:fractionalOffset:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, edges, ObjCRuntime.ObjCStructArg(fractionalOffset, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"))) as MemorySegment
        }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property edges
    fun edges(): NSDirectionalRectEdge {
        val sel = ObjCRuntime.sel("edges")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDirectionalRectEdge
    }
    
    // @property offset
    fun offset(): NSPoint {
        val sel = ObjCRuntime.sel("offset")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint"), ptr, sel) as NSPoint
    }
    
    // @property isAbsoluteOffset
    fun isAbsoluteOffset(): BOOL {
        val sel = ObjCRuntime.sel("isAbsoluteOffset")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property isFractionalOffset
    fun isFractionalOffset(): BOOL {
        val sel = ObjCRuntime.sel("isFractionalOffset")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}


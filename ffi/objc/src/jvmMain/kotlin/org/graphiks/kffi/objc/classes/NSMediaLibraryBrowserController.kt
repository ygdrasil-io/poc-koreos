/**
 * Kotlin/JVM wrapper for Objective-C class: NSMediaLibraryBrowserController
 * Superclass: NSObject
 */
open class NSMediaLibraryBrowserController(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMediaLibraryBrowserController") }
        
        fun sharedMediaLibraryBrowserController(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedMediaLibraryBrowserController")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun togglePanel(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("togglePanel:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    // @property sharedMediaLibraryBrowserController
    fun sharedMediaLibraryBrowserController(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedMediaLibraryBrowserController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property visible
    fun isVisible(): BOOL {
        val sel = ObjCRuntime.sel("isVisible")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setVisible(value: BOOL) {
        val sel = ObjCRuntime.sel("setVisible:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property frame
    fun frame(): NSRect {
        val sel = ObjCRuntime.sel("frame")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    fun setFrame(value: NSRect) {
        val sel = ObjCRuntime.sel("setFrame:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property mediaLibraries
    fun mediaLibraries(): NSMediaLibrary {
        val sel = ObjCRuntime.sel("mediaLibraries")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSMediaLibrary
    }
    fun setMediaLibraries(value: NSMediaLibrary) {
        val sel = ObjCRuntime.sel("setMediaLibraries:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


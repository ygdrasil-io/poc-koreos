/**
 * Kotlin/JVM wrapper for Objective-C class: NSWorkspaceOpenConfiguration
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSWorkspaceOpenConfiguration(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSWorkspaceOpenConfiguration") }
        
        fun configuration(): MemorySegment {
            val sel = ObjCRuntime.sel("configuration")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property promptsUserIfNeeded
    fun promptsUserIfNeeded(): BOOL {
        val sel = ObjCRuntime.sel("promptsUserIfNeeded")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setPromptsUserIfNeeded(value: BOOL) {
        val sel = ObjCRuntime.sel("setPromptsUserIfNeeded:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property addsToRecentItems
    fun addsToRecentItems(): BOOL {
        val sel = ObjCRuntime.sel("addsToRecentItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAddsToRecentItems(value: BOOL) {
        val sel = ObjCRuntime.sel("setAddsToRecentItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property activates
    fun activates(): BOOL {
        val sel = ObjCRuntime.sel("activates")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setActivates(value: BOOL) {
        val sel = ObjCRuntime.sel("setActivates:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hides
    fun hides(): BOOL {
        val sel = ObjCRuntime.sel("hides")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHides(value: BOOL) {
        val sel = ObjCRuntime.sel("setHides:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property hidesOthers
    fun hidesOthers(): BOOL {
        val sel = ObjCRuntime.sel("hidesOthers")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHidesOthers(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidesOthers:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property forPrinting
    fun isForPrinting(): BOOL {
        val sel = ObjCRuntime.sel("isForPrinting")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setForPrinting(value: BOOL) {
        val sel = ObjCRuntime.sel("setForPrinting:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property createsNewApplicationInstance
    fun createsNewApplicationInstance(): BOOL {
        val sel = ObjCRuntime.sel("createsNewApplicationInstance")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCreatesNewApplicationInstance(value: BOOL) {
        val sel = ObjCRuntime.sel("setCreatesNewApplicationInstance:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowsRunningApplicationSubstitution
    fun allowsRunningApplicationSubstitution(): BOOL {
        val sel = ObjCRuntime.sel("allowsRunningApplicationSubstitution")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAllowsRunningApplicationSubstitution(value: BOOL) {
        val sel = ObjCRuntime.sel("setAllowsRunningApplicationSubstitution:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property arguments
    /** @return NSArray<NSString *> * */
    fun arguments(): MemorySegment {
        val sel = ObjCRuntime.sel("arguments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setArguments(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setArguments:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property environment
    /** @return NSDictionary<NSString *,NSString *> * */
    fun environment(): MemorySegment {
        val sel = ObjCRuntime.sel("environment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setEnvironment(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEnvironment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property appleEvent
    fun appleEvent(): MemorySegment {
        val sel = ObjCRuntime.sel("appleEvent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAppleEvent(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAppleEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property architecture
    fun architecture(): cpu_type_t {
        val sel = ObjCRuntime.sel("architecture")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as cpu_type_t
    }
    fun setArchitecture(value: cpu_type_t) {
        val sel = ObjCRuntime.sel("setArchitecture:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property requiresUniversalLinks
    fun requiresUniversalLinks(): BOOL {
        val sel = ObjCRuntime.sel("requiresUniversalLinks")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRequiresUniversalLinks(value: BOOL) {
        val sel = ObjCRuntime.sel("setRequiresUniversalLinks:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


/**
 * Kotlin/JVM wrapper for Objective-C class: NSKeyValueSharedObservers
 * Superclass: NSObject
 */
open class NSKeyValueSharedObservers(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSKeyValueSharedObservers") }
        
        fun new(): MemorySegment {
            val sel = ObjCRuntime.sel("new")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithObservableClass(observableClass: Class): MemorySegment {
        val sel = ObjCRuntime.sel("initWithObservableClass:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, observableClass) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun addSharedObserver_forKey_options_context(observer: MemorySegment, key: MemorySegment, options: NSKeyValueObservingOptions, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addSharedObserver:forKey:options:context:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, key, options, context)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun addSharedObserver_forKey_options_context(observer: MemorySegment, key: String, options: NSKeyValueObservingOptions, context: MemorySegment): Unit = addSharedObserver_forKey_options_context(observer, ObjCRuntime.newNSString(Arena.global(), key), options, context)
    
    fun addObserver_forKeyPath_options_context(observer: MemorySegment, keyPath: MemorySegment, options: NSKeyValueObservingOptions, context: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addObserver:forKeyPath:options:context:")
        ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath, options, context)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun addObserver_forKeyPath_options_context(observer: MemorySegment, keyPath: String, options: NSKeyValueObservingOptions, context: MemorySegment): Unit = addObserver_forKeyPath_options_context(observer, ObjCRuntime.newNSString(Arena.global(), keyPath), options, context)
    
    fun snapshot(): MemorySegment {
        val sel = ObjCRuntime.sel("snapshot")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


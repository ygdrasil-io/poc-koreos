/**
 * Kotlin/JVM wrapper for Objective-C class: NSDataAsset
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSDataAsset(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSDataAsset") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithName(name: NSDataAssetName): MemorySegment {
        val sel = ObjCRuntime.sel("initWithName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
    }
    
    fun initWithName_bundle(name: NSDataAssetName, bundle: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithName:bundle:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, bundle) as MemorySegment
    }
    
    // @property name
    fun name(): NSDataAssetName {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDataAssetName
    }
    
    // @property data
    fun `data`(): MemorySegment {
        val sel = ObjCRuntime.sel("data")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property typeIdentifier
    fun typeIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("typeIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun typeIdentifierAsString(): String = ObjCRuntime.toJavaString(typeIdentifier())
    
}


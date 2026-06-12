package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSKeyedArchiver
 * Superclass: NSCoder
 */
open class NSKeyedArchiver(ptr: MemorySegment) : NSCoder(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSKeyedArchiver") }
        
        fun archivedDataWithRootObject_requiringSecureCoding_error(`object`: MemorySegment, requiresSecureCoding: BOOL, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("archivedDataWithRootObject:requiringSecureCoding:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `object`, requiresSecureCoding, error) as MemorySegment
        }
        
        fun archivedDataWithRootObject(rootObject: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("archivedDataWithRootObject:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, rootObject) as MemorySegment
        }
        
        fun archiveRootObject_toFile(rootObject: MemorySegment, path: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("archiveRootObject:toFile:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, rootObject, path) as BOOL
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun archiveRootObject_toFile(rootObject: MemorySegment, path: String): BOOL = archiveRootObject_toFile(rootObject, ObjCRuntime.newNSString(Arena.global(), path))
        
        fun setClassName_forClass(codedName: MemorySegment, cls: Class<*>): Unit {
            val sel = ObjCRuntime.sel("setClassName:forClass:")
            ObjCRuntime.msgSend(null, _class, sel, codedName, cls)
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun setClassName_forClass(codedName: String, cls: Class<*>): Unit = setClassName_forClass(ObjCRuntime.newNSString(Arena.global(), codedName), cls)
        
        fun classNameForClass(cls: Class<*>): MemorySegment {
            val sel = ObjCRuntime.sel("classNameForClass:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cls) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        fun classNameForClassAsString(cls: Class<*>): String = ObjCRuntime.toJavaString(classNameForClass(cls))
        
    }
    
    fun initRequiringSecureCoding(requiresSecureCoding: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initRequiringSecureCoding:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, requiresSecureCoding) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initForWritingWithMutableData(`data`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initForWritingWithMutableData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`) as MemorySegment
    }
    
    fun finishEncoding(): Unit {
        val sel = ObjCRuntime.sel("finishEncoding")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setClassName_forClass(codedName: MemorySegment, cls: Class<*>): Unit {
        val sel = ObjCRuntime.sel("setClassName:forClass:")
        ObjCRuntime.msgSend(null, ptr, sel, codedName, cls)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setClassName_forClass(codedName: String, cls: Class<*>): Unit = setClassName_forClass(ObjCRuntime.newNSString(Arena.global(), codedName), cls)
    
    fun classNameForClass(cls: Class<*>): MemorySegment {
        val sel = ObjCRuntime.sel("classNameForClass:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, cls) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun classNameForClassAsString(cls: Class<*>): String = ObjCRuntime.toJavaString(classNameForClass(cls))
    
    override fun `encodeObject_forKey`(`object`: MemorySegment, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeObject:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeObject_forKey(`object`: MemorySegment, key: String): Unit = encodeObject_forKey(`object`, ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `encodeConditionalObject_forKey`(`object`: MemorySegment, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeConditionalObject:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, `object`, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeConditionalObject_forKey(`object`: MemorySegment, key: String): Unit = encodeConditionalObject_forKey(`object`, ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `encodeBool_forKey`(value: BOOL, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeBool:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeBool_forKey(value: BOOL, key: String): Unit = encodeBool_forKey(value, ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `encodeInt_forKey`(value: Int, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeInt:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeInt_forKey(value: Int, key: String): Unit = encodeInt_forKey(value, ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `encodeInt32_forKey`(value: int32_t, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeInt32:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeInt32_forKey(value: int32_t, key: String): Unit = encodeInt32_forKey(value, ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `encodeInt64_forKey`(value: int64_t, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeInt64:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeInt64_forKey(value: int64_t, key: String): Unit = encodeInt64_forKey(value, ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `encodeFloat_forKey`(value: Float, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeFloat:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeFloat_forKey(value: Float, key: String): Unit = encodeFloat_forKey(value, ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `encodeDouble_forKey`(value: Double, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeDouble:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, value, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeDouble_forKey(value: Double, key: String): Unit = encodeDouble_forKey(value, ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `encodeBytes_length_forKey`(bytes: MemorySegment, length: NSUInteger, key: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("encodeBytes:length:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, bytes, length, key)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun encodeBytes_length_forKey(bytes: MemorySegment, length: NSUInteger, key: String): Unit = encodeBytes_length_forKey(bytes, length, ObjCRuntime.newNSString(Arena.global(), key))
    
    // @property delegate
    /** @return id<NSKeyedArchiverDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property outputFormat
    fun outputFormat(): NSPropertyListFormat {
        val sel = ObjCRuntime.sel("outputFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPropertyListFormat
    }
    fun setOutputFormat(value: NSPropertyListFormat) {
        val sel = ObjCRuntime.sel("setOutputFormat:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property encodedData
    fun encodedData(): MemorySegment {
        val sel = ObjCRuntime.sel("encodedData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property requiresSecureCoding
    override fun `requiresSecureCoding`(): BOOL {
        val sel = ObjCRuntime.sel("requiresSecureCoding")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setRequiresSecureCoding(value: BOOL) {
        val sel = ObjCRuntime.sel("setRequiresSecureCoding:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


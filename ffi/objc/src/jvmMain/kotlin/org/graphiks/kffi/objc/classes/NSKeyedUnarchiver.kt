package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSKeyedUnarchiver
 * Superclass: NSCoder
 */
open class NSKeyedUnarchiver(ptr: MemorySegment) : NSCoder(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSKeyedUnarchiver") }
        
        fun unarchivedObjectOfClass_fromData_error(cls: Class<*>, `data`: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchivedObjectOfClass:fromData:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cls, `data`, error) as MemorySegment
        }
        
        fun unarchivedArrayOfObjectsOfClass_fromData_error(cls: Class<*>, `data`: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchivedArrayOfObjectsOfClass:fromData:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cls, `data`, error) as MemorySegment
        }
        
        fun unarchivedDictionaryWithKeysOfClass_objectsOfClass_fromData_error(keyCls: Class<*>, valueCls: Class<*>, `data`: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchivedDictionaryWithKeysOfClass:objectsOfClass:fromData:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, keyCls, valueCls, `data`, error) as MemorySegment
        }
        
        fun unarchivedObjectOfClasses_fromData_error(classes: MemorySegment, `data`: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchivedObjectOfClasses:fromData:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, classes, `data`, error) as MemorySegment
        }
        
        fun unarchivedArrayOfObjectsOfClasses_fromData_error(classes: MemorySegment, `data`: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchivedArrayOfObjectsOfClasses:fromData:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, classes, `data`, error) as MemorySegment
        }
        
        fun unarchivedDictionaryWithKeysOfClasses_objectsOfClasses_fromData_error(keyClasses: MemorySegment, valueClasses: MemorySegment, `data`: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchivedDictionaryWithKeysOfClasses:objectsOfClasses:fromData:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, keyClasses, valueClasses, `data`, error) as MemorySegment
        }
        
        fun unarchiveObjectWithData(`data`: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchiveObjectWithData:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`) as MemorySegment
        }
        
        fun unarchiveTopLevelObjectWithData_error(`data`: MemorySegment, error: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchiveTopLevelObjectWithData:error:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, `data`, error) as MemorySegment
        }
        
        fun unarchiveObjectWithFile(path: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unarchiveObjectWithFile:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, path) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun unarchiveObjectWithFile(path: String): MemorySegment = unarchiveObjectWithFile(ObjCRuntime.newNSString(Arena.global(), path))
        
        fun setClass_forClassName(cls: Class<*>, codedName: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setClass:forClassName:")
            ObjCRuntime.msgSend(null, _class, sel, cls, codedName)
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun setClass_forClassName(cls: Class<*>, codedName: String): Unit = setClass_forClassName(cls, ObjCRuntime.newNSString(Arena.global(), codedName))
        
        fun classForClassName(codedName: MemorySegment): Class<*> {
            val sel = ObjCRuntime.sel("classForClassName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, codedName) as Class<*>
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun classForClassName(codedName: String): Class<*> = classForClassName(ObjCRuntime.newNSString(Arena.global(), codedName))
        
    }
    
    fun initForReadingFromData_error(`data`: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initForReadingFromData:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`, error) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initForReadingWithData(`data`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initForReadingWithData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`) as MemorySegment
    }
    
    fun finishDecoding(): Unit {
        val sel = ObjCRuntime.sel("finishDecoding")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setClass_forClassName(cls: Class<*>, codedName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setClass:forClassName:")
        ObjCRuntime.msgSend(null, ptr, sel, cls, codedName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setClass_forClassName(cls: Class<*>, codedName: String): Unit = setClass_forClassName(cls, ObjCRuntime.newNSString(Arena.global(), codedName))
    
    fun classForClassName(codedName: MemorySegment): Class<*> {
        val sel = ObjCRuntime.sel("classForClassName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, codedName) as Class<*>
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun classForClassName(codedName: String): Class<*> = classForClassName(ObjCRuntime.newNSString(Arena.global(), codedName))
    
    override fun `containsValueForKey`(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("containsValueForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun containsValueForKey(key: String): BOOL = containsValueForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `decodeObjectForKey`(key: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("decodeObjectForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun decodeObjectForKey(key: String): MemorySegment = decodeObjectForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `decodeBoolForKey`(key: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("decodeBoolForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, key) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun decodeBoolForKey(key: String): BOOL = decodeBoolForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `decodeIntForKey`(key: MemorySegment): Int {
        val sel = ObjCRuntime.sel("decodeIntForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, key) as Int
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun decodeIntForKey(key: String): Int = decodeIntForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `decodeInt32ForKey`(key: MemorySegment): int32_t {
        val sel = ObjCRuntime.sel("decodeInt32ForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, key) as int32_t
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun decodeInt32ForKey(key: String): int32_t = decodeInt32ForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `decodeInt64ForKey`(key: MemorySegment): int64_t {
        val sel = ObjCRuntime.sel("decodeInt64ForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, key) as int64_t
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun decodeInt64ForKey(key: String): int64_t = decodeInt64ForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `decodeFloatForKey`(key: MemorySegment): Float {
        val sel = ObjCRuntime.sel("decodeFloatForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel, key) as Float
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun decodeFloatForKey(key: String): Float = decodeFloatForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `decodeDoubleForKey`(key: MemorySegment): Double {
        val sel = ObjCRuntime.sel("decodeDoubleForKey:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel, key) as Double
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun decodeDoubleForKey(key: String): Double = decodeDoubleForKey(ObjCRuntime.newNSString(Arena.global(), key))
    
    override fun `decodeBytesForKey_returnedLength`(key: MemorySegment, lengthp: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("decodeBytesForKey:returnedLength:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key, lengthp) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun decodeBytesForKey_returnedLength(key: String, lengthp: MemorySegment): MemorySegment = decodeBytesForKey_returnedLength(ObjCRuntime.newNSString(Arena.global(), key), lengthp)
    
    // @property delegate
    /** @return id<NSKeyedUnarchiverDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
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
    
    // @property decodingFailurePolicy
    override fun `decodingFailurePolicy`(): NSDecodingFailurePolicy {
        val sel = ObjCRuntime.sel("decodingFailurePolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSDecodingFailurePolicy
    }
    fun setDecodingFailurePolicy(value: NSDecodingFailurePolicy) {
        val sel = ObjCRuntime.sel("setDecodingFailurePolicy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}


/**
 * Kotlin/JVM wrapper for Objective-C class: NSAppleEventDescriptor
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSAppleEventDescriptor(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSAppleEventDescriptor") }
        
        fun nullDescriptor(): MemorySegment {
            val sel = ObjCRuntime.sel("nullDescriptor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun descriptorWithDescriptorType_bytes_length(descriptorType: DescType, bytes: MemorySegment, byteCount: NSUInteger): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithDescriptorType:bytes:length:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, descriptorType, bytes, byteCount) as MemorySegment
        }
        
        fun descriptorWithDescriptorType_data(descriptorType: DescType, `data`: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithDescriptorType:data:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, descriptorType, `data`) as MemorySegment
        }
        
        fun descriptorWithBoolean(boolean: Boolean): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithBoolean:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, boolean) as MemorySegment
        }
        
        fun descriptorWithEnumCode(enumerator: OSType): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithEnumCode:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, enumerator) as MemorySegment
        }
        
        fun descriptorWithInt32(signedInt: SInt32): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithInt32:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, signedInt) as MemorySegment
        }
        
        fun descriptorWithDouble(doubleValue: Double): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithDouble:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, doubleValue) as MemorySegment
        }
        
        fun descriptorWithTypeCode(typeCode: OSType): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithTypeCode:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, typeCode) as MemorySegment
        }
        
        fun descriptorWithString(string: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, string) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun descriptorWithString(string: String): MemorySegment = descriptorWithString(ObjCRuntime.newNSString(Arena.global(), string))
        
        fun descriptorWithDate(date: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithDate:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, date) as MemorySegment
        }
        
        fun descriptorWithFileURL(fileURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithFileURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, fileURL) as MemorySegment
        }
        
        fun appleEventWithEventClass_eventID_targetDescriptor_returnID_transactionID(eventClass: AEEventClass, eventID: AEEventID, targetDescriptor: MemorySegment, returnID: AEReturnID, transactionID: AETransactionID): MemorySegment {
            val sel = ObjCRuntime.sel("appleEventWithEventClass:eventID:targetDescriptor:returnID:transactionID:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, eventClass, eventID, targetDescriptor, returnID, transactionID) as MemorySegment
        }
        
        fun listDescriptor(): MemorySegment {
            val sel = ObjCRuntime.sel("listDescriptor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun recordDescriptor(): MemorySegment {
            val sel = ObjCRuntime.sel("recordDescriptor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun currentProcessDescriptor(): MemorySegment {
            val sel = ObjCRuntime.sel("currentProcessDescriptor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun descriptorWithProcessIdentifier(processIdentifier: pid_t): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithProcessIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, processIdentifier) as MemorySegment
        }
        
        fun descriptorWithBundleIdentifier(bundleIdentifier: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithBundleIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, bundleIdentifier) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun descriptorWithBundleIdentifier(bundleIdentifier: String): MemorySegment = descriptorWithBundleIdentifier(ObjCRuntime.newNSString(Arena.global(), bundleIdentifier))
        
        fun descriptorWithApplicationURL(applicationURL: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("descriptorWithApplicationURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, applicationURL) as MemorySegment
        }
        
    }
    
    fun initWithAEDescNoCopy(aeDesc: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithAEDescNoCopy:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, aeDesc) as MemorySegment
    }
    
    fun initWithDescriptorType_bytes_length(descriptorType: DescType, bytes: MemorySegment, byteCount: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDescriptorType:bytes:length:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, descriptorType, bytes, byteCount) as MemorySegment
    }
    
    fun initWithDescriptorType_data(descriptorType: DescType, `data`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDescriptorType:data:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, descriptorType, `data`) as MemorySegment
    }
    
    fun initWithEventClass_eventID_targetDescriptor_returnID_transactionID(eventClass: AEEventClass, eventID: AEEventID, targetDescriptor: MemorySegment, returnID: AEReturnID, transactionID: AETransactionID): MemorySegment {
        val sel = ObjCRuntime.sel("initWithEventClass:eventID:targetDescriptor:returnID:transactionID:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, eventClass, eventID, targetDescriptor, returnID, transactionID) as MemorySegment
    }
    
    fun initListDescriptor(): MemorySegment {
        val sel = ObjCRuntime.sel("initListDescriptor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initRecordDescriptor(): MemorySegment {
        val sel = ObjCRuntime.sel("initRecordDescriptor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun setParamDescriptor_forKeyword(descriptor: MemorySegment, keyword: AEKeyword): Unit {
        val sel = ObjCRuntime.sel("setParamDescriptor:forKeyword:")
        ObjCRuntime.msgSend(null, ptr, sel, descriptor, keyword)
    }
    
    fun paramDescriptorForKeyword(keyword: AEKeyword): MemorySegment {
        val sel = ObjCRuntime.sel("paramDescriptorForKeyword:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keyword) as MemorySegment
    }
    
    fun removeParamDescriptorWithKeyword(keyword: AEKeyword): Unit {
        val sel = ObjCRuntime.sel("removeParamDescriptorWithKeyword:")
        ObjCRuntime.msgSend(null, ptr, sel, keyword)
    }
    
    fun setAttributeDescriptor_forKeyword(descriptor: MemorySegment, keyword: AEKeyword): Unit {
        val sel = ObjCRuntime.sel("setAttributeDescriptor:forKeyword:")
        ObjCRuntime.msgSend(null, ptr, sel, descriptor, keyword)
    }
    
    fun attributeDescriptorForKeyword(keyword: AEKeyword): MemorySegment {
        val sel = ObjCRuntime.sel("attributeDescriptorForKeyword:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keyword) as MemorySegment
    }
    
    fun sendEventWithOptions_timeout_error(sendOptions: NSAppleEventSendOptions, timeoutInSeconds: NSTimeInterval, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("sendEventWithOptions:timeout:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sendOptions, timeoutInSeconds, error) as MemorySegment
    }
    
    fun insertDescriptor_atIndex(descriptor: MemorySegment, index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("insertDescriptor:atIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, descriptor, index)
    }
    
    fun descriptorAtIndex(index: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("descriptorAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    fun removeDescriptorAtIndex(index: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeDescriptorAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, index)
    }
    
    fun setDescriptor_forKeyword(descriptor: MemorySegment, keyword: AEKeyword): Unit {
        val sel = ObjCRuntime.sel("setDescriptor:forKeyword:")
        ObjCRuntime.msgSend(null, ptr, sel, descriptor, keyword)
    }
    
    fun descriptorForKeyword(keyword: AEKeyword): MemorySegment {
        val sel = ObjCRuntime.sel("descriptorForKeyword:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keyword) as MemorySegment
    }
    
    fun removeDescriptorWithKeyword(keyword: AEKeyword): Unit {
        val sel = ObjCRuntime.sel("removeDescriptorWithKeyword:")
        ObjCRuntime.msgSend(null, ptr, sel, keyword)
    }
    
    fun keywordForDescriptorAtIndex(index: NSInteger): AEKeyword {
        val sel = ObjCRuntime.sel("keywordForDescriptorAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel, index) as AEKeyword
    }
    
    fun coerceToDescriptorType(descriptorType: DescType): MemorySegment {
        val sel = ObjCRuntime.sel("coerceToDescriptorType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, descriptorType) as MemorySegment
    }
    
    // @property aeDesc
    fun aeDesc(): MemorySegment {
        val sel = ObjCRuntime.sel("aeDesc")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property descriptorType
    fun descriptorType(): DescType {
        val sel = ObjCRuntime.sel("descriptorType")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as DescType
    }
    
    // @property data
    fun `data`(): MemorySegment {
        val sel = ObjCRuntime.sel("data")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property booleanValue
    fun booleanValue(): Boolean {
        val sel = ObjCRuntime.sel("booleanValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BYTE, ptr, sel) as Boolean
    }
    
    // @property enumCodeValue
    fun enumCodeValue(): OSType {
        val sel = ObjCRuntime.sel("enumCodeValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as OSType
    }
    
    // @property int32Value
    fun int32Value(): SInt32 {
        val sel = ObjCRuntime.sel("int32Value")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as SInt32
    }
    
    // @property doubleValue
    fun doubleValue(): Double {
        val sel = ObjCRuntime.sel("doubleValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as Double
    }
    
    // @property typeCodeValue
    fun typeCodeValue(): OSType {
        val sel = ObjCRuntime.sel("typeCodeValue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as OSType
    }
    
    // @property stringValue
    fun stringValue(): MemorySegment {
        val sel = ObjCRuntime.sel("stringValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringValueAsString(): String = ObjCRuntime.toJavaString(stringValue())
    
    // @property dateValue
    fun dateValue(): MemorySegment {
        val sel = ObjCRuntime.sel("dateValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fileURLValue
    fun fileURLValue(): MemorySegment {
        val sel = ObjCRuntime.sel("fileURLValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property eventClass
    fun eventClass(): AEEventClass {
        val sel = ObjCRuntime.sel("eventClass")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as AEEventClass
    }
    
    // @property eventID
    fun eventID(): AEEventID {
        val sel = ObjCRuntime.sel("eventID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as AEEventID
    }
    
    // @property returnID
    fun returnID(): AEReturnID {
        val sel = ObjCRuntime.sel("returnID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_SHORT, ptr, sel) as AEReturnID
    }
    
    // @property transactionID
    fun transactionID(): AETransactionID {
        val sel = ObjCRuntime.sel("transactionID")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as AETransactionID
    }
    
    // @property isRecordDescriptor
    fun isRecordDescriptor(): BOOL {
        val sel = ObjCRuntime.sel("isRecordDescriptor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property numberOfItems
    fun numberOfItems(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfItems")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _desc: AEDesc
    // ivar: _hasValidDesc: BOOL
    // ivar: _padding: MemorySegment
}


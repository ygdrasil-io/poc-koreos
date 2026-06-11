/**
 * Kotlin/JVM wrapper for Objective-C class: NSColorSpace
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSColorSpace(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColorSpace") }
        
        /** @return NSArray<NSColorSpace *> * */
        fun availableColorSpacesWithModel(model: NSColorSpaceModel): MemorySegment {
            val sel = ObjCRuntime.sel("availableColorSpacesWithModel:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, model) as MemorySegment
        }
        
        fun sRGBColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("sRGBColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun genericGamma22GrayColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("genericGamma22GrayColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun extendedSRGBColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("extendedSRGBColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun extendedGenericGamma22GrayColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("extendedGenericGamma22GrayColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun displayP3ColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("displayP3ColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun adobeRGB1998ColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("adobeRGB1998ColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun genericRGBColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("genericRGBColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun genericGrayColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("genericGrayColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun genericCMYKColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("genericCMYKColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun deviceRGBColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("deviceRGBColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun deviceGrayColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("deviceGrayColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun deviceCMYKColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("deviceCMYKColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithICCProfileData(iccData: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithICCProfileData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, iccData) as MemorySegment
    }
    
    fun initWithColorSyncProfile(prof: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithColorSyncProfile:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, prof) as MemorySegment
    }
    
    fun initWithCGColorSpace(cgColorSpace: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGColorSpace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, cgColorSpace) as MemorySegment
    }
    
    // @property ICCProfileData
    fun ICCProfileData(): MemorySegment {
        val sel = ObjCRuntime.sel("ICCProfileData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property colorSyncProfile
    fun colorSyncProfile(): MemorySegment {
        val sel = ObjCRuntime.sel("colorSyncProfile")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property CGColorSpace
    fun CGColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("CGColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfColorComponents
    fun numberOfColorComponents(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfColorComponents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property colorSpaceModel
    fun colorSpaceModel(): NSColorSpaceModel {
        val sel = ObjCRuntime.sel("colorSpaceModel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorSpaceModel
    }
    
    // @property localizedName
    fun localizedName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedNameAsString(): String = ObjCRuntime.toJavaString(localizedName())
    
    // @property sRGBColorSpace
    fun sRGBColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("sRGBColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property genericGamma22GrayColorSpace
    fun genericGamma22GrayColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("genericGamma22GrayColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property extendedSRGBColorSpace
    fun extendedSRGBColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("extendedSRGBColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property extendedGenericGamma22GrayColorSpace
    fun extendedGenericGamma22GrayColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("extendedGenericGamma22GrayColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property displayP3ColorSpace
    fun displayP3ColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("displayP3ColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property adobeRGB1998ColorSpace
    fun adobeRGB1998ColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("adobeRGB1998ColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property genericRGBColorSpace
    fun genericRGBColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("genericRGBColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property genericGrayColorSpace
    fun genericGrayColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("genericGrayColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property genericCMYKColorSpace
    fun genericCMYKColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("genericCMYKColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property deviceRGBColorSpace
    fun deviceRGBColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("deviceRGBColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property deviceGrayColorSpace
    fun deviceGrayColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("deviceGrayColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property deviceCMYKColorSpace
    fun deviceCMYKColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("deviceCMYKColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


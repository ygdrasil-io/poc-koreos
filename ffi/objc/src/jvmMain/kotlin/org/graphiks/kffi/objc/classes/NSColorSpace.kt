package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSColorSpace
 * Superclass: NSObject
 * Protocols: NSSecureCoding
 */
open class NSColorSpace(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColorSpace") }
        
        /** @return NSArray<NSColorSpace *> * */
        open fun availableColorSpacesWithModel(model: NSColorSpaceModel): MemorySegment {
            val sel = ObjCRuntime.sel("availableColorSpacesWithModel:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, model) as MemorySegment
        }
        
        open fun sRGBColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("sRGBColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun genericGamma22GrayColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("genericGamma22GrayColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun extendedSRGBColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("extendedSRGBColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun extendedGenericGamma22GrayColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("extendedGenericGamma22GrayColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun displayP3ColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("displayP3ColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun adobeRGB1998ColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("adobeRGB1998ColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun genericRGBColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("genericRGBColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun genericGrayColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("genericGrayColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun genericCMYKColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("genericCMYKColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun deviceRGBColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("deviceRGBColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun deviceGrayColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("deviceGrayColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun deviceCMYKColorSpace(): MemorySegment {
            val sel = ObjCRuntime.sel("deviceCMYKColorSpace")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithICCProfileData(iccData: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithICCProfileData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, iccData) as MemorySegment
    }
    
    open fun initWithColorSyncProfile(prof: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithColorSyncProfile:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, prof) as MemorySegment
    }
    
    open fun initWithCGColorSpace(cgColorSpace: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGColorSpace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, cgColorSpace) as MemorySegment
    }
    
    // @property ICCProfileData
    open fun ICCProfileData(): MemorySegment {
        val sel = ObjCRuntime.sel("ICCProfileData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property colorSyncProfile
    open fun colorSyncProfile(): MemorySegment {
        val sel = ObjCRuntime.sel("colorSyncProfile")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property CGColorSpace
    open fun CGColorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("CGColorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfColorComponents
    open fun numberOfColorComponents(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfColorComponents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property colorSpaceModel
    open fun colorSpaceModel(): NSColorSpaceModel {
        val sel = ObjCRuntime.sel("colorSpaceModel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorSpaceModel
    }
    
    // @property localizedName
    open fun localizedName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedNameAsString(): String = ObjCRuntime.toJavaString(localizedName())
    
    // @property sRGBColorSpace
    }
    
    // @property genericGamma22GrayColorSpace
    }
    
    // @property extendedSRGBColorSpace
    }
    
    // @property extendedGenericGamma22GrayColorSpace
    }
    
    // @property displayP3ColorSpace
    }
    
    // @property adobeRGB1998ColorSpace
    }
    
    // @property genericRGBColorSpace
    }
    
    // @property genericGrayColorSpace
    }
    
    // @property genericCMYKColorSpace
    }
    
    // @property deviceRGBColorSpace
    }
    
    // @property deviceGrayColorSpace
    }
    
    // @property deviceCMYKColorSpace
    }
    
}


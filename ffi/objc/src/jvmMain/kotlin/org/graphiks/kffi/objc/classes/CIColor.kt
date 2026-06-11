/**
 * Kotlin/JVM wrapper for Objective-C class: CIColor
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class CIColor(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("CIColor") }
        
        fun colorWithCGColor(color: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithCGColor:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, color) as MemorySegment
        }
        
        fun colorWithRed_green_blue_alpha(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithRed:green:blue:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha) as MemorySegment
        }
        
        fun colorWithRed_green_blue(red: CGFloat, green: CGFloat, blue: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithRed:green:blue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue) as MemorySegment
        }
        
        fun colorWithRed_green_blue_alpha_colorSpace(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat, colorSpace: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithRed:green:blue:alpha:colorSpace:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha, colorSpace) as MemorySegment
        }
        
        fun colorWithRed_green_blue_colorSpace(red: CGFloat, green: CGFloat, blue: CGFloat, colorSpace: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithRed:green:blue:colorSpace:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, colorSpace) as MemorySegment
        }
        
        fun colorWithString(representation: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, representation) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun colorWithString(representation: String): MemorySegment = colorWithString(ObjCRuntime.newNSString(Arena.global(), representation))
        
        fun blackColor(): MemorySegment {
            val sel = ObjCRuntime.sel("blackColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun whiteColor(): MemorySegment {
            val sel = ObjCRuntime.sel("whiteColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun grayColor(): MemorySegment {
            val sel = ObjCRuntime.sel("grayColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun redColor(): MemorySegment {
            val sel = ObjCRuntime.sel("redColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun greenColor(): MemorySegment {
            val sel = ObjCRuntime.sel("greenColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun blueColor(): MemorySegment {
            val sel = ObjCRuntime.sel("blueColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cyanColor(): MemorySegment {
            val sel = ObjCRuntime.sel("cyanColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun magentaColor(): MemorySegment {
            val sel = ObjCRuntime.sel("magentaColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun yellowColor(): MemorySegment {
            val sel = ObjCRuntime.sel("yellowColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun clearColor(): MemorySegment {
            val sel = ObjCRuntime.sel("clearColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithCGColor(color: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCGColor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, color) as MemorySegment
    }
    
    fun initWithRed_green_blue_alpha(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRed:green:blue:alpha:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, red, green, blue, alpha) as MemorySegment
    }
    
    fun initWithRed_green_blue(red: CGFloat, green: CGFloat, blue: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRed:green:blue:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, red, green, blue) as MemorySegment
    }
    
    fun initWithRed_green_blue_alpha_colorSpace(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat, colorSpace: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRed:green:blue:alpha:colorSpace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, red, green, blue, alpha, colorSpace) as MemorySegment
    }
    
    fun initWithRed_green_blue_colorSpace(red: CGFloat, green: CGFloat, blue: CGFloat, colorSpace: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithRed:green:blue:colorSpace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, red, green, blue, colorSpace) as MemorySegment
    }
    
    // @property numberOfComponents
    fun numberOfComponents(): size_t {
        val sel = ObjCRuntime.sel("numberOfComponents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as size_t
    }
    
    // @property components
    fun components(): MemorySegment {
        val sel = ObjCRuntime.sel("components")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property alpha
    fun alpha(): CGFloat {
        val sel = ObjCRuntime.sel("alpha")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property colorSpace
    fun colorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("colorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property red
    fun red(): CGFloat {
        val sel = ObjCRuntime.sel("red")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property green
    fun green(): CGFloat {
        val sel = ObjCRuntime.sel("green")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property blue
    fun blue(): CGFloat {
        val sel = ObjCRuntime.sel("blue")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property stringRepresentation
    fun stringRepresentation(): MemorySegment {
        val sel = ObjCRuntime.sel("stringRepresentation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringRepresentationAsString(): String = ObjCRuntime.toJavaString(stringRepresentation())
    
    // @property blackColor
    fun blackColor(): MemorySegment {
        val sel = ObjCRuntime.sel("blackColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property whiteColor
    fun whiteColor(): MemorySegment {
        val sel = ObjCRuntime.sel("whiteColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property grayColor
    fun grayColor(): MemorySegment {
        val sel = ObjCRuntime.sel("grayColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property redColor
    fun redColor(): MemorySegment {
        val sel = ObjCRuntime.sel("redColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property greenColor
    fun greenColor(): MemorySegment {
        val sel = ObjCRuntime.sel("greenColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property blueColor
    fun blueColor(): MemorySegment {
        val sel = ObjCRuntime.sel("blueColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cyanColor
    fun cyanColor(): MemorySegment {
        val sel = ObjCRuntime.sel("cyanColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property magentaColor
    fun magentaColor(): MemorySegment {
        val sel = ObjCRuntime.sel("magentaColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property yellowColor
    fun yellowColor(): MemorySegment {
        val sel = ObjCRuntime.sel("yellowColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property clearColor
    fun clearColor(): MemorySegment {
        val sel = ObjCRuntime.sel("clearColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _priv: MemorySegment
    // ivar: _pad: MemorySegment
}

// ── Category: NSAppKitAdditions on CIColor ─────────────────────────────────────────

fun CIColor.initWithColor(color: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithColor:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, color) as MemorySegment
}


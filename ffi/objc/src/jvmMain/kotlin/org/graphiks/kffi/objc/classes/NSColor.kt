/**
 * Kotlin/JVM wrapper for Objective-C class: NSColor
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding, NSPasteboardReading, NSPasteboardWriting
 */
open class NSColor(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSColor") }
        
        fun colorWithColorSpace_components_count(space: MemorySegment, components: MemorySegment, numberOfComponents: NSInteger): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithColorSpace:components:count:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, space, components, numberOfComponents) as MemorySegment
        }
        
        fun colorWithSRGBRed_green_blue_alpha(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithSRGBRed:green:blue:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha) as MemorySegment
        }
        
        fun colorWithGenericGamma22White_alpha(white: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithGenericGamma22White:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, white, alpha) as MemorySegment
        }
        
        fun colorWithDisplayP3Red_green_blue_alpha(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithDisplayP3Red:green:blue:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha) as MemorySegment
        }
        
        fun colorWithWhite_alpha(white: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithWhite:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, white, alpha) as MemorySegment
        }
        
        fun colorWithRed_green_blue_alpha(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithRed:green:blue:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha) as MemorySegment
        }
        
        fun colorWithHue_saturation_brightness_alpha(hue: CGFloat, saturation: CGFloat, brightness: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithHue:saturation:brightness:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, hue, saturation, brightness, alpha) as MemorySegment
        }
        
        fun colorWithColorSpace_hue_saturation_brightness_alpha(space: MemorySegment, hue: CGFloat, saturation: CGFloat, brightness: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithColorSpace:hue:saturation:brightness:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, space, hue, saturation, brightness, alpha) as MemorySegment
        }
        
        fun colorWithCatalogName_colorName(listName: NSColorListName, colorName: NSColorName): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithCatalogName:colorName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, listName, colorName) as MemorySegment
        }
        
        fun colorNamed_bundle(name: NSColorName, bundle: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorNamed:bundle:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, bundle) as MemorySegment
        }
        
        fun colorNamed(name: NSColorName): MemorySegment {
            val sel = ObjCRuntime.sel("colorNamed:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        fun colorWithName_dynamicProvider(colorName: NSColorName, dynamicProvider: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithName:dynamicProvider:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, colorName, dynamicProvider) as MemorySegment
        }
        
        fun colorWithDeviceWhite_alpha(white: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithDeviceWhite:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, white, alpha) as MemorySegment
        }
        
        fun colorWithDeviceRed_green_blue_alpha(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithDeviceRed:green:blue:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha) as MemorySegment
        }
        
        fun colorWithDeviceHue_saturation_brightness_alpha(hue: CGFloat, saturation: CGFloat, brightness: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithDeviceHue:saturation:brightness:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, hue, saturation, brightness, alpha) as MemorySegment
        }
        
        fun colorWithDeviceCyan_magenta_yellow_black_alpha(cyan: CGFloat, magenta: CGFloat, yellow: CGFloat, black: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithDeviceCyan:magenta:yellow:black:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cyan, magenta, yellow, black, alpha) as MemorySegment
        }
        
        fun colorWithCalibratedWhite_alpha(white: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithCalibratedWhite:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, white, alpha) as MemorySegment
        }
        
        fun colorWithCalibratedRed_green_blue_alpha(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithCalibratedRed:green:blue:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha) as MemorySegment
        }
        
        fun colorWithCalibratedHue_saturation_brightness_alpha(hue: CGFloat, saturation: CGFloat, brightness: CGFloat, alpha: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithCalibratedHue:saturation:brightness:alpha:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, hue, saturation, brightness, alpha) as MemorySegment
        }
        
        fun colorWithPatternImage(image: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithPatternImage:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, image) as MemorySegment
        }
        
        fun colorWithRed_green_blue_alpha_exposure(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat, exposure: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithRed:green:blue:alpha:exposure:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha, exposure) as MemorySegment
        }
        
        fun colorWithRed_green_blue_alpha_linearExposure(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat, linearExposure: CGFloat): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithRed:green:blue:alpha:linearExposure:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, red, green, blue, alpha, linearExposure) as MemorySegment
        }
        
        fun colorForControlTint(controlTint: NSControlTint): MemorySegment {
            val sel = ObjCRuntime.sel("colorForControlTint:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, controlTint) as MemorySegment
        }
        
        fun colorFromPasteboard(pasteBoard: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorFromPasteboard:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, pasteBoard) as MemorySegment
        }
        
        fun colorWithCGColor(cgColor: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("colorWithCGColor:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, cgColor) as MemorySegment
        }
        
        fun blackColor(): MemorySegment {
            val sel = ObjCRuntime.sel("blackColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun darkGrayColor(): MemorySegment {
            val sel = ObjCRuntime.sel("darkGrayColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun lightGrayColor(): MemorySegment {
            val sel = ObjCRuntime.sel("lightGrayColor")
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
        
        fun yellowColor(): MemorySegment {
            val sel = ObjCRuntime.sel("yellowColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun magentaColor(): MemorySegment {
            val sel = ObjCRuntime.sel("magentaColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun orangeColor(): MemorySegment {
            val sel = ObjCRuntime.sel("orangeColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun purpleColor(): MemorySegment {
            val sel = ObjCRuntime.sel("purpleColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun brownColor(): MemorySegment {
            val sel = ObjCRuntime.sel("brownColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun clearColor(): MemorySegment {
            val sel = ObjCRuntime.sel("clearColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun labelColor(): MemorySegment {
            val sel = ObjCRuntime.sel("labelColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun secondaryLabelColor(): MemorySegment {
            val sel = ObjCRuntime.sel("secondaryLabelColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun tertiaryLabelColor(): MemorySegment {
            val sel = ObjCRuntime.sel("tertiaryLabelColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun quaternaryLabelColor(): MemorySegment {
            val sel = ObjCRuntime.sel("quaternaryLabelColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun quinaryLabelColor(): MemorySegment {
            val sel = ObjCRuntime.sel("quinaryLabelColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun linkColor(): MemorySegment {
            val sel = ObjCRuntime.sel("linkColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun placeholderTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("placeholderTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun windowFrameTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("windowFrameTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun selectedMenuItemTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("selectedMenuItemTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun alternateSelectedControlTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("alternateSelectedControlTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun headerTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("headerTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun separatorColor(): MemorySegment {
            val sel = ObjCRuntime.sel("separatorColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun gridColor(): MemorySegment {
            val sel = ObjCRuntime.sel("gridColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun windowBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("windowBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun underPageBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("underPageBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun controlBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("controlBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun selectedContentBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("selectedContentBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun unemphasizedSelectedContentBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("unemphasizedSelectedContentBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        /** @return NSArray<NSColor *> * */
        fun alternatingContentBackgroundColors(): MemorySegment {
            val sel = ObjCRuntime.sel("alternatingContentBackgroundColors")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun findHighlightColor(): MemorySegment {
            val sel = ObjCRuntime.sel("findHighlightColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun textColor(): MemorySegment {
            val sel = ObjCRuntime.sel("textColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun textBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("textBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun textInsertionPointColor(): MemorySegment {
            val sel = ObjCRuntime.sel("textInsertionPointColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun selectedTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("selectedTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun selectedTextBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("selectedTextBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun unemphasizedSelectedTextBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("unemphasizedSelectedTextBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun unemphasizedSelectedTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("unemphasizedSelectedTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun controlColor(): MemorySegment {
            val sel = ObjCRuntime.sel("controlColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun controlTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("controlTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun selectedControlColor(): MemorySegment {
            val sel = ObjCRuntime.sel("selectedControlColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun selectedControlTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("selectedControlTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun disabledControlTextColor(): MemorySegment {
            val sel = ObjCRuntime.sel("disabledControlTextColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun keyboardFocusIndicatorColor(): MemorySegment {
            val sel = ObjCRuntime.sel("keyboardFocusIndicatorColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun scrubberTexturedBackgroundColor(): MemorySegment {
            val sel = ObjCRuntime.sel("scrubberTexturedBackgroundColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemRedColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemRedColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemGreenColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemGreenColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemBlueColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemBlueColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemOrangeColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemOrangeColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemYellowColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemYellowColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemBrownColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemBrownColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemPinkColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemPinkColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemPurpleColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemPurpleColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemGrayColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemGrayColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemTealColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemTealColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemIndigoColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemIndigoColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemMintColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemMintColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemCyanColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemCyanColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun systemFillColor(): MemorySegment {
            val sel = ObjCRuntime.sel("systemFillColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun secondarySystemFillColor(): MemorySegment {
            val sel = ObjCRuntime.sel("secondarySystemFillColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun tertiarySystemFillColor(): MemorySegment {
            val sel = ObjCRuntime.sel("tertiarySystemFillColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun quaternarySystemFillColor(): MemorySegment {
            val sel = ObjCRuntime.sel("quaternarySystemFillColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun quinarySystemFillColor(): MemorySegment {
            val sel = ObjCRuntime.sel("quinarySystemFillColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun controlAccentColor(): MemorySegment {
            val sel = ObjCRuntime.sel("controlAccentColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun currentControlTint(): NSControlTint {
            val sel = ObjCRuntime.sel("currentControlTint")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as NSControlTint
        }
        
        fun highlightColor(): MemorySegment {
            val sel = ObjCRuntime.sel("highlightColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun shadowColor(): MemorySegment {
            val sel = ObjCRuntime.sel("shadowColor")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun ignoresAlpha(): BOOL {
            val sel = ObjCRuntime.sel("ignoresAlpha")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun setIgnoresAlpha(ignoresAlpha: BOOL): Unit {
            val sel = ObjCRuntime.sel("setIgnoresAlpha:")
            ObjCRuntime.msgSend(null, _class, sel, ignoresAlpha)
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun colorUsingType(type: NSColorType): MemorySegment {
        val sel = ObjCRuntime.sel("colorUsingType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
    }
    
    fun colorUsingColorSpace(space: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("colorUsingColorSpace:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, space) as MemorySegment
    }
    
    fun colorByApplyingContentHeadroom(contentHeadroom: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("colorByApplyingContentHeadroom:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, contentHeadroom) as MemorySegment
    }
    
    fun highlightWithLevel(`val`: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("highlightWithLevel:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `val`) as MemorySegment
    }
    
    fun shadowWithLevel(`val`: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("shadowWithLevel:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `val`) as MemorySegment
    }
    
    fun colorWithSystemEffect(systemEffect: NSColorSystemEffect): MemorySegment {
        val sel = ObjCRuntime.sel("colorWithSystemEffect:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, systemEffect) as MemorySegment
    }
    
    fun `set`(): Unit {
        val sel = ObjCRuntime.sel("set")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setFill(): Unit {
        val sel = ObjCRuntime.sel("setFill")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun setStroke(): Unit {
        val sel = ObjCRuntime.sel("setStroke")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun blendedColorWithFraction_ofColor(fraction: CGFloat, color: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("blendedColorWithFraction:ofColor:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fraction, color) as MemorySegment
    }
    
    fun colorWithAlphaComponent(alpha: CGFloat): MemorySegment {
        val sel = ObjCRuntime.sel("colorWithAlphaComponent:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, alpha) as MemorySegment
    }
    
    fun getRed_green_blue_alpha(red: MemorySegment, green: MemorySegment, blue: MemorySegment, alpha: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getRed:green:blue:alpha:")
        ObjCRuntime.msgSend(null, ptr, sel, red, green, blue, alpha)
    }
    
    fun getHue_saturation_brightness_alpha(hue: MemorySegment, saturation: MemorySegment, brightness: MemorySegment, alpha: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getHue:saturation:brightness:alpha:")
        ObjCRuntime.msgSend(null, ptr, sel, hue, saturation, brightness, alpha)
    }
    
    fun getWhite_alpha(white: MemorySegment, alpha: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getWhite:alpha:")
        ObjCRuntime.msgSend(null, ptr, sel, white, alpha)
    }
    
    fun getCyan_magenta_yellow_black_alpha(cyan: MemorySegment, magenta: MemorySegment, yellow: MemorySegment, black: MemorySegment, alpha: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getCyan:magenta:yellow:black:alpha:")
        ObjCRuntime.msgSend(null, ptr, sel, cyan, magenta, yellow, black, alpha)
    }
    
    fun getComponents(components: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("getComponents:")
        ObjCRuntime.msgSend(null, ptr, sel, components)
    }
    
    fun writeToPasteboard(pasteBoard: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("writeToPasteboard:")
        ObjCRuntime.msgSend(null, ptr, sel, pasteBoard)
    }
    
    fun drawSwatchInRect(rect: NSRect): Unit {
        val sel = ObjCRuntime.sel("drawSwatchInRect:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(rect, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")))
    }
    
    // @property type
    fun type(): NSColorType {
        val sel = ObjCRuntime.sel("type")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorType
    }
    
    // @property standardDynamicRangeColor
    fun standardDynamicRangeColor(): MemorySegment {
        val sel = ObjCRuntime.sel("standardDynamicRangeColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property blackColor
    fun blackColor(): MemorySegment {
        val sel = ObjCRuntime.sel("blackColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property darkGrayColor
    fun darkGrayColor(): MemorySegment {
        val sel = ObjCRuntime.sel("darkGrayColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property lightGrayColor
    fun lightGrayColor(): MemorySegment {
        val sel = ObjCRuntime.sel("lightGrayColor")
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
    
    // @property yellowColor
    fun yellowColor(): MemorySegment {
        val sel = ObjCRuntime.sel("yellowColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property magentaColor
    fun magentaColor(): MemorySegment {
        val sel = ObjCRuntime.sel("magentaColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property orangeColor
    fun orangeColor(): MemorySegment {
        val sel = ObjCRuntime.sel("orangeColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property purpleColor
    fun purpleColor(): MemorySegment {
        val sel = ObjCRuntime.sel("purpleColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property brownColor
    fun brownColor(): MemorySegment {
        val sel = ObjCRuntime.sel("brownColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property clearColor
    fun clearColor(): MemorySegment {
        val sel = ObjCRuntime.sel("clearColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property labelColor
    fun labelColor(): MemorySegment {
        val sel = ObjCRuntime.sel("labelColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property secondaryLabelColor
    fun secondaryLabelColor(): MemorySegment {
        val sel = ObjCRuntime.sel("secondaryLabelColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property tertiaryLabelColor
    fun tertiaryLabelColor(): MemorySegment {
        val sel = ObjCRuntime.sel("tertiaryLabelColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property quaternaryLabelColor
    fun quaternaryLabelColor(): MemorySegment {
        val sel = ObjCRuntime.sel("quaternaryLabelColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property quinaryLabelColor
    fun quinaryLabelColor(): MemorySegment {
        val sel = ObjCRuntime.sel("quinaryLabelColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property linkColor
    fun linkColor(): MemorySegment {
        val sel = ObjCRuntime.sel("linkColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property placeholderTextColor
    fun placeholderTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("placeholderTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property windowFrameTextColor
    fun windowFrameTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("windowFrameTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedMenuItemTextColor
    fun selectedMenuItemTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedMenuItemTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property alternateSelectedControlTextColor
    fun alternateSelectedControlTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("alternateSelectedControlTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property headerTextColor
    fun headerTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("headerTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property separatorColor
    fun separatorColor(): MemorySegment {
        val sel = ObjCRuntime.sel("separatorColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property gridColor
    fun gridColor(): MemorySegment {
        val sel = ObjCRuntime.sel("gridColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property windowBackgroundColor
    fun windowBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("windowBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property underPageBackgroundColor
    fun underPageBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("underPageBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property controlBackgroundColor
    fun controlBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("controlBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedContentBackgroundColor
    fun selectedContentBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedContentBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property unemphasizedSelectedContentBackgroundColor
    fun unemphasizedSelectedContentBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("unemphasizedSelectedContentBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property alternatingContentBackgroundColors
    /** @return NSArray<NSColor *> * */
    fun alternatingContentBackgroundColors(): MemorySegment {
        val sel = ObjCRuntime.sel("alternatingContentBackgroundColors")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property findHighlightColor
    fun findHighlightColor(): MemorySegment {
        val sel = ObjCRuntime.sel("findHighlightColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textColor
    fun textColor(): MemorySegment {
        val sel = ObjCRuntime.sel("textColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textBackgroundColor
    fun textBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("textBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property textInsertionPointColor
    fun textInsertionPointColor(): MemorySegment {
        val sel = ObjCRuntime.sel("textInsertionPointColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedTextColor
    fun selectedTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedTextBackgroundColor
    fun selectedTextBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedTextBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property unemphasizedSelectedTextBackgroundColor
    fun unemphasizedSelectedTextBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("unemphasizedSelectedTextBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property unemphasizedSelectedTextColor
    fun unemphasizedSelectedTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("unemphasizedSelectedTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property controlColor
    fun controlColor(): MemorySegment {
        val sel = ObjCRuntime.sel("controlColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property controlTextColor
    fun controlTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("controlTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedControlColor
    fun selectedControlColor(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedControlColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectedControlTextColor
    fun selectedControlTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedControlTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property disabledControlTextColor
    fun disabledControlTextColor(): MemorySegment {
        val sel = ObjCRuntime.sel("disabledControlTextColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property keyboardFocusIndicatorColor
    fun keyboardFocusIndicatorColor(): MemorySegment {
        val sel = ObjCRuntime.sel("keyboardFocusIndicatorColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property scrubberTexturedBackgroundColor
    fun scrubberTexturedBackgroundColor(): MemorySegment {
        val sel = ObjCRuntime.sel("scrubberTexturedBackgroundColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemRedColor
    fun systemRedColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemRedColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemGreenColor
    fun systemGreenColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemGreenColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemBlueColor
    fun systemBlueColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemBlueColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemOrangeColor
    fun systemOrangeColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemOrangeColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemYellowColor
    fun systemYellowColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemYellowColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemBrownColor
    fun systemBrownColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemBrownColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemPinkColor
    fun systemPinkColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemPinkColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemPurpleColor
    fun systemPurpleColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemPurpleColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemGrayColor
    fun systemGrayColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemGrayColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemTealColor
    fun systemTealColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemTealColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemIndigoColor
    fun systemIndigoColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemIndigoColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemMintColor
    fun systemMintColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemMintColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemCyanColor
    fun systemCyanColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemCyanColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property systemFillColor
    fun systemFillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("systemFillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property secondarySystemFillColor
    fun secondarySystemFillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("secondarySystemFillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property tertiarySystemFillColor
    fun tertiarySystemFillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("tertiarySystemFillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property quaternarySystemFillColor
    fun quaternarySystemFillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("quaternarySystemFillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property quinarySystemFillColor
    fun quinarySystemFillColor(): MemorySegment {
        val sel = ObjCRuntime.sel("quinarySystemFillColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property controlAccentColor
    fun controlAccentColor(): MemorySegment {
        val sel = ObjCRuntime.sel("controlAccentColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property currentControlTint
    fun currentControlTint(): NSControlTint {
        val sel = ObjCRuntime.sel("currentControlTint")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSControlTint
    }
    
    // @property highlightColor
    fun highlightColor(): MemorySegment {
        val sel = ObjCRuntime.sel("highlightColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shadowColor
    fun shadowColor(): MemorySegment {
        val sel = ObjCRuntime.sel("shadowColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property catalogNameComponent
    fun catalogNameComponent(): NSColorListName {
        val sel = ObjCRuntime.sel("catalogNameComponent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorListName
    }
    
    // @property colorNameComponent
    fun colorNameComponent(): NSColorName {
        val sel = ObjCRuntime.sel("colorNameComponent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorName
    }
    
    // @property localizedCatalogNameComponent
    fun localizedCatalogNameComponent(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedCatalogNameComponent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedCatalogNameComponentAsString(): String = ObjCRuntime.toJavaString(localizedCatalogNameComponent())
    
    // @property localizedColorNameComponent
    fun localizedColorNameComponent(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedColorNameComponent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedColorNameComponentAsString(): String = ObjCRuntime.toJavaString(localizedColorNameComponent())
    
    // @property redComponent
    fun redComponent(): CGFloat {
        val sel = ObjCRuntime.sel("redComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property greenComponent
    fun greenComponent(): CGFloat {
        val sel = ObjCRuntime.sel("greenComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property blueComponent
    fun blueComponent(): CGFloat {
        val sel = ObjCRuntime.sel("blueComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property hueComponent
    fun hueComponent(): CGFloat {
        val sel = ObjCRuntime.sel("hueComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property saturationComponent
    fun saturationComponent(): CGFloat {
        val sel = ObjCRuntime.sel("saturationComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property brightnessComponent
    fun brightnessComponent(): CGFloat {
        val sel = ObjCRuntime.sel("brightnessComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property whiteComponent
    fun whiteComponent(): CGFloat {
        val sel = ObjCRuntime.sel("whiteComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property cyanComponent
    fun cyanComponent(): CGFloat {
        val sel = ObjCRuntime.sel("cyanComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property magentaComponent
    fun magentaComponent(): CGFloat {
        val sel = ObjCRuntime.sel("magentaComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property yellowComponent
    fun yellowComponent(): CGFloat {
        val sel = ObjCRuntime.sel("yellowComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property blackComponent
    fun blackComponent(): CGFloat {
        val sel = ObjCRuntime.sel("blackComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property colorSpace
    fun colorSpace(): MemorySegment {
        val sel = ObjCRuntime.sel("colorSpace")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfComponents
    fun numberOfComponents(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfComponents")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property patternImage
    fun patternImage(): MemorySegment {
        val sel = ObjCRuntime.sel("patternImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property alphaComponent
    fun alphaComponent(): CGFloat {
        val sel = ObjCRuntime.sel("alphaComponent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property linearExposure
    fun linearExposure(): CGFloat {
        val sel = ObjCRuntime.sel("linearExposure")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    
    // @property CGColor
    fun CGColor(): MemorySegment {
        val sel = ObjCRuntime.sel("CGColor")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property ignoresAlpha
    fun ignoresAlpha(): BOOL {
        val sel = ObjCRuntime.sel("ignoresAlpha")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIgnoresAlpha(value: BOOL) {
        val sel = ObjCRuntime.sel("setIgnoresAlpha:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSColor ─────────────────────────────────────────

fun NSColor.colorUsingColorSpaceName_device(name: NSColorSpaceName, deviceDescription: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("colorUsingColorSpaceName:device:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, deviceDescription) as MemorySegment
}

fun NSColor.colorUsingColorSpaceName(name: NSColorSpaceName): MemorySegment {
    val sel = ObjCRuntime.sel("colorUsingColorSpaceName:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name) as MemorySegment
}

fun NSColor.colorSpaceName(): NSColorSpaceName {
    val sel = ObjCRuntime.sel("colorSpaceName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorSpaceName
}

// Class method: +[NSColor controlHighlightColor]
fun NSColor_controlHighlightColor(): MemorySegment {
    val sel = ObjCRuntime.sel("controlHighlightColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor controlLightHighlightColor]
fun NSColor_controlLightHighlightColor(): MemorySegment {
    val sel = ObjCRuntime.sel("controlLightHighlightColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor controlShadowColor]
fun NSColor_controlShadowColor(): MemorySegment {
    val sel = ObjCRuntime.sel("controlShadowColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor controlDarkShadowColor]
fun NSColor_controlDarkShadowColor(): MemorySegment {
    val sel = ObjCRuntime.sel("controlDarkShadowColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor scrollBarColor]
fun NSColor_scrollBarColor(): MemorySegment {
    val sel = ObjCRuntime.sel("scrollBarColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor knobColor]
fun NSColor_knobColor(): MemorySegment {
    val sel = ObjCRuntime.sel("knobColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor selectedKnobColor]
fun NSColor_selectedKnobColor(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedKnobColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor windowFrameColor]
fun NSColor_windowFrameColor(): MemorySegment {
    val sel = ObjCRuntime.sel("windowFrameColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor selectedMenuItemColor]
fun NSColor_selectedMenuItemColor(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedMenuItemColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor headerColor]
fun NSColor_headerColor(): MemorySegment {
    val sel = ObjCRuntime.sel("headerColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor secondarySelectedControlColor]
fun NSColor_secondarySelectedControlColor(): MemorySegment {
    val sel = ObjCRuntime.sel("secondarySelectedControlColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor alternateSelectedControlColor]
fun NSColor_alternateSelectedControlColor(): MemorySegment {
    val sel = ObjCRuntime.sel("alternateSelectedControlColor")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSColor controlAlternatingRowBackgroundColors]
fun NSColor_controlAlternatingRowBackgroundColors(): MemorySegment {
    val sel = ObjCRuntime.sel("controlAlternatingRowBackgroundColors")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// @property controlHighlightColor
fun NSColor.controlHighlightColor(): MemorySegment {
    val sel = ObjCRuntime.sel("controlHighlightColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property controlLightHighlightColor
fun NSColor.controlLightHighlightColor(): MemorySegment {
    val sel = ObjCRuntime.sel("controlLightHighlightColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property controlShadowColor
fun NSColor.controlShadowColor(): MemorySegment {
    val sel = ObjCRuntime.sel("controlShadowColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property controlDarkShadowColor
fun NSColor.controlDarkShadowColor(): MemorySegment {
    val sel = ObjCRuntime.sel("controlDarkShadowColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property scrollBarColor
fun NSColor.scrollBarColor(): MemorySegment {
    val sel = ObjCRuntime.sel("scrollBarColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property knobColor
fun NSColor.knobColor(): MemorySegment {
    val sel = ObjCRuntime.sel("knobColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property selectedKnobColor
fun NSColor.selectedKnobColor(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedKnobColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property windowFrameColor
fun NSColor.windowFrameColor(): MemorySegment {
    val sel = ObjCRuntime.sel("windowFrameColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property selectedMenuItemColor
fun NSColor.selectedMenuItemColor(): MemorySegment {
    val sel = ObjCRuntime.sel("selectedMenuItemColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property headerColor
fun NSColor.headerColor(): MemorySegment {
    val sel = ObjCRuntime.sel("headerColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property secondarySelectedControlColor
fun NSColor.secondarySelectedControlColor(): MemorySegment {
    val sel = ObjCRuntime.sel("secondarySelectedControlColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property alternateSelectedControlColor
fun NSColor.alternateSelectedControlColor(): MemorySegment {
    val sel = ObjCRuntime.sel("alternateSelectedControlColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property controlAlternatingRowBackgroundColors
/** @return NSArray<NSColor *> * */
fun NSColor.controlAlternatingRowBackgroundColors(): MemorySegment {
    val sel = ObjCRuntime.sel("controlAlternatingRowBackgroundColors")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property colorSpaceName
fun NSColor.colorSpaceName(): NSColorSpaceName {
    val sel = ObjCRuntime.sel("colorSpaceName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSColorSpaceName
}

// ── Category: NSQuartzCoreAdditions on NSColor ─────────────────────────────────────────

// Class method: +[NSColor colorWithCIColor:]
fun NSColor_colorWithCIColor(color: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("colorWithCIColor:")
    val cls = ObjCRuntime.getClass("NSColor")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, color) as MemorySegment
}

// ── Category: NSAccessibilityColorConformance on NSColor ─────────────────────────────────────────


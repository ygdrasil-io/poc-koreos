/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitVolume
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitVolume(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitVolume") }
        
        fun megaliters(): MemorySegment {
            val sel = ObjCRuntime.sel("megaliters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kiloliters(): MemorySegment {
            val sel = ObjCRuntime.sel("kiloliters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun liters(): MemorySegment {
            val sel = ObjCRuntime.sel("liters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun deciliters(): MemorySegment {
            val sel = ObjCRuntime.sel("deciliters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun centiliters(): MemorySegment {
            val sel = ObjCRuntime.sel("centiliters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun milliliters(): MemorySegment {
            val sel = ObjCRuntime.sel("milliliters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicKilometers(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicKilometers")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicMeters(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicMeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicDecimeters(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicDecimeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicCentimeters(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicCentimeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicMillimeters(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicMillimeters")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicInches(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicInches")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicFeet(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicFeet")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicYards(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicYards")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cubicMiles(): MemorySegment {
            val sel = ObjCRuntime.sel("cubicMiles")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun acreFeet(): MemorySegment {
            val sel = ObjCRuntime.sel("acreFeet")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun bushels(): MemorySegment {
            val sel = ObjCRuntime.sel("bushels")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun teaspoons(): MemorySegment {
            val sel = ObjCRuntime.sel("teaspoons")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun tablespoons(): MemorySegment {
            val sel = ObjCRuntime.sel("tablespoons")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun fluidOunces(): MemorySegment {
            val sel = ObjCRuntime.sel("fluidOunces")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun cups(): MemorySegment {
            val sel = ObjCRuntime.sel("cups")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun pints(): MemorySegment {
            val sel = ObjCRuntime.sel("pints")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun quarts(): MemorySegment {
            val sel = ObjCRuntime.sel("quarts")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun gallons(): MemorySegment {
            val sel = ObjCRuntime.sel("gallons")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun imperialTeaspoons(): MemorySegment {
            val sel = ObjCRuntime.sel("imperialTeaspoons")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun imperialTablespoons(): MemorySegment {
            val sel = ObjCRuntime.sel("imperialTablespoons")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun imperialFluidOunces(): MemorySegment {
            val sel = ObjCRuntime.sel("imperialFluidOunces")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun imperialPints(): MemorySegment {
            val sel = ObjCRuntime.sel("imperialPints")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun imperialQuarts(): MemorySegment {
            val sel = ObjCRuntime.sel("imperialQuarts")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun imperialGallons(): MemorySegment {
            val sel = ObjCRuntime.sel("imperialGallons")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun metricCups(): MemorySegment {
            val sel = ObjCRuntime.sel("metricCups")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property megaliters
    fun megaliters(): MemorySegment {
        val sel = ObjCRuntime.sel("megaliters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property kiloliters
    fun kiloliters(): MemorySegment {
        val sel = ObjCRuntime.sel("kiloliters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property liters
    fun liters(): MemorySegment {
        val sel = ObjCRuntime.sel("liters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property deciliters
    fun deciliters(): MemorySegment {
        val sel = ObjCRuntime.sel("deciliters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property centiliters
    fun centiliters(): MemorySegment {
        val sel = ObjCRuntime.sel("centiliters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property milliliters
    fun milliliters(): MemorySegment {
        val sel = ObjCRuntime.sel("milliliters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicKilometers
    fun cubicKilometers(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicKilometers")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicMeters
    fun cubicMeters(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicMeters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicDecimeters
    fun cubicDecimeters(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicDecimeters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicCentimeters
    fun cubicCentimeters(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicCentimeters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicMillimeters
    fun cubicMillimeters(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicMillimeters")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicInches
    fun cubicInches(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicInches")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicFeet
    fun cubicFeet(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicFeet")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicYards
    fun cubicYards(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicYards")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cubicMiles
    fun cubicMiles(): MemorySegment {
        val sel = ObjCRuntime.sel("cubicMiles")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property acreFeet
    fun acreFeet(): MemorySegment {
        val sel = ObjCRuntime.sel("acreFeet")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property bushels
    fun bushels(): MemorySegment {
        val sel = ObjCRuntime.sel("bushels")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property teaspoons
    fun teaspoons(): MemorySegment {
        val sel = ObjCRuntime.sel("teaspoons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property tablespoons
    fun tablespoons(): MemorySegment {
        val sel = ObjCRuntime.sel("tablespoons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property fluidOunces
    fun fluidOunces(): MemorySegment {
        val sel = ObjCRuntime.sel("fluidOunces")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cups
    fun cups(): MemorySegment {
        val sel = ObjCRuntime.sel("cups")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property pints
    fun pints(): MemorySegment {
        val sel = ObjCRuntime.sel("pints")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property quarts
    fun quarts(): MemorySegment {
        val sel = ObjCRuntime.sel("quarts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property gallons
    fun gallons(): MemorySegment {
        val sel = ObjCRuntime.sel("gallons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property imperialTeaspoons
    fun imperialTeaspoons(): MemorySegment {
        val sel = ObjCRuntime.sel("imperialTeaspoons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property imperialTablespoons
    fun imperialTablespoons(): MemorySegment {
        val sel = ObjCRuntime.sel("imperialTablespoons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property imperialFluidOunces
    fun imperialFluidOunces(): MemorySegment {
        val sel = ObjCRuntime.sel("imperialFluidOunces")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property imperialPints
    fun imperialPints(): MemorySegment {
        val sel = ObjCRuntime.sel("imperialPints")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property imperialQuarts
    fun imperialQuarts(): MemorySegment {
        val sel = ObjCRuntime.sel("imperialQuarts")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property imperialGallons
    fun imperialGallons(): MemorySegment {
        val sel = ObjCRuntime.sel("imperialGallons")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property metricCups
    fun metricCups(): MemorySegment {
        val sel = ObjCRuntime.sel("metricCups")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


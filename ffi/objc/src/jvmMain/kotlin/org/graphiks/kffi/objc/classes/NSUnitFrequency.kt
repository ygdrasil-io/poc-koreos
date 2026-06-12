package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSUnitFrequency
 * Superclass: NSDimension
 * Protocols: NSSecureCoding
 */
open class NSUnitFrequency(ptr: MemorySegment) : NSDimension(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUnitFrequency") }
        
        fun terahertz(): MemorySegment {
            val sel = ObjCRuntime.sel("terahertz")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun gigahertz(): MemorySegment {
            val sel = ObjCRuntime.sel("gigahertz")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun megahertz(): MemorySegment {
            val sel = ObjCRuntime.sel("megahertz")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun kilohertz(): MemorySegment {
            val sel = ObjCRuntime.sel("kilohertz")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun hertz(): MemorySegment {
            val sel = ObjCRuntime.sel("hertz")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun millihertz(): MemorySegment {
            val sel = ObjCRuntime.sel("millihertz")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun microhertz(): MemorySegment {
            val sel = ObjCRuntime.sel("microhertz")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun nanohertz(): MemorySegment {
            val sel = ObjCRuntime.sel("nanohertz")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun framesPerSecond(): MemorySegment {
            val sel = ObjCRuntime.sel("framesPerSecond")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    // @property terahertz
    }
    
    // @property gigahertz
    }
    
    // @property megahertz
    }
    
    // @property kilohertz
    }
    
    // @property hertz
    }
    
    // @property millihertz
    }
    
    // @property microhertz
    }
    
    // @property nanohertz
    }
    
    // @property framesPerSecond
    }
    
}


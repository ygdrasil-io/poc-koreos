package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPDFInfo
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSPDFInfo(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPDFInfo") }
        
    }
    
    // @property URL
    open fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property fileExtensionHidden
    open fun isFileExtensionHidden(): BOOL {
        val sel = ObjCRuntime.sel("isFileExtensionHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setFileExtensionHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setFileExtensionHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tagNames
    /** @return NSArray<NSString *> * */
    open fun tagNames(): MemorySegment {
        val sel = ObjCRuntime.sel("tagNames")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTagNames(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTagNames:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property orientation
    open fun orientation(): NSPaperOrientation {
        val sel = ObjCRuntime.sel("orientation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPaperOrientation
    }
    open fun setOrientation(value: NSPaperOrientation) {
        val sel = ObjCRuntime.sel("setOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property paperSize
    open fun paperSize(): NSSize {
        val sel = ObjCRuntime.sel("paperSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    open fun setPaperSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setPaperSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property attributes
    /** @return NSMutableDictionary<NSPrintInfoAttributeKey,id> * */
    open fun attributes(): MemorySegment {
        val sel = ObjCRuntime.sel("attributes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


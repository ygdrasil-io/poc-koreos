package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSPrintInfo
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSPrintInfo(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPrintInfo") }
        
        open fun sharedPrintInfo(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedPrintInfo")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun setSharedPrintInfo(sharedPrintInfo: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setSharedPrintInfo:")
            ObjCRuntime.msgSend(null, _class, sel, sharedPrintInfo)
        }
        
        open fun defaultPrinter(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultPrinter")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithDictionary(attributes: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDictionary:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attributes) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** @return NSMutableDictionary<NSPrintInfoAttributeKey,id> * */
    open fun dictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("dictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun setUpPrintOperationDefaultValues(): Unit {
        val sel = ObjCRuntime.sel("setUpPrintOperationDefaultValues")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun PMPrintSession(): MemorySegment {
        val sel = ObjCRuntime.sel("PMPrintSession")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun PMPageFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("PMPageFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun PMPrintSettings(): MemorySegment {
        val sel = ObjCRuntime.sel("PMPrintSettings")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun updateFromPMPageFormat(): Unit {
        val sel = ObjCRuntime.sel("updateFromPMPageFormat")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun updateFromPMPrintSettings(): Unit {
        val sel = ObjCRuntime.sel("updateFromPMPrintSettings")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun takeSettingsFromPDFInfo(inPDFInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeSettingsFromPDFInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, inPDFInfo)
    }
    
    // @property sharedPrintInfo
    open fun paperName(): NSPrinterPaperName {
        val sel = ObjCRuntime.sel("paperName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrinterPaperName
    }
    open fun setPaperName(value: NSPrinterPaperName) {
        val sel = ObjCRuntime.sel("setPaperName:")
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
    
    // @property orientation
    open fun orientation(): NSPaperOrientation {
        val sel = ObjCRuntime.sel("orientation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPaperOrientation
    }
    open fun setOrientation(value: NSPaperOrientation) {
        val sel = ObjCRuntime.sel("setOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scalingFactor
    open fun scalingFactor(): CGFloat {
        val sel = ObjCRuntime.sel("scalingFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setScalingFactor(value: CGFloat) {
        val sel = ObjCRuntime.sel("setScalingFactor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property leftMargin
    open fun leftMargin(): CGFloat {
        val sel = ObjCRuntime.sel("leftMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setLeftMargin(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLeftMargin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rightMargin
    open fun rightMargin(): CGFloat {
        val sel = ObjCRuntime.sel("rightMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setRightMargin(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRightMargin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property topMargin
    open fun topMargin(): CGFloat {
        val sel = ObjCRuntime.sel("topMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setTopMargin(value: CGFloat) {
        val sel = ObjCRuntime.sel("setTopMargin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bottomMargin
    open fun bottomMargin(): CGFloat {
        val sel = ObjCRuntime.sel("bottomMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setBottomMargin(value: CGFloat) {
        val sel = ObjCRuntime.sel("setBottomMargin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property horizontallyCentered
    open fun isHorizontallyCentered(): BOOL {
        val sel = ObjCRuntime.sel("isHorizontallyCentered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setHorizontallyCentered(value: BOOL) {
        val sel = ObjCRuntime.sel("setHorizontallyCentered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticallyCentered
    open fun isVerticallyCentered(): BOOL {
        val sel = ObjCRuntime.sel("isVerticallyCentered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setVerticallyCentered(value: BOOL) {
        val sel = ObjCRuntime.sel("setVerticallyCentered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property horizontalPagination
    open fun horizontalPagination(): NSPrintingPaginationMode {
        val sel = ObjCRuntime.sel("horizontalPagination")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrintingPaginationMode
    }
    open fun setHorizontalPagination(value: NSPrintingPaginationMode) {
        val sel = ObjCRuntime.sel("setHorizontalPagination:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticalPagination
    open fun verticalPagination(): NSPrintingPaginationMode {
        val sel = ObjCRuntime.sel("verticalPagination")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrintingPaginationMode
    }
    open fun setVerticalPagination(value: NSPrintingPaginationMode) {
        val sel = ObjCRuntime.sel("setVerticalPagination:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property jobDisposition
    open fun jobDisposition(): NSPrintJobDispositionValue {
        val sel = ObjCRuntime.sel("jobDisposition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrintJobDispositionValue
    }
    open fun setJobDisposition(value: NSPrintJobDispositionValue) {
        val sel = ObjCRuntime.sel("setJobDisposition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property printer
    open fun printer(): MemorySegment {
        val sel = ObjCRuntime.sel("printer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setPrinter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPrinter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageablePageBounds
    open fun imageablePageBounds(): NSRect {
        val sel = ObjCRuntime.sel("imageablePageBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property localizedPaperName
    open fun localizedPaperName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedPaperName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedPaperNameAsString(): String = ObjCRuntime.toJavaString(localizedPaperName())
    
    // @property defaultPrinter
    /** @return NSMutableDictionary<NSPrintInfoSettingKey,id> * */
    open fun printSettings(): MemorySegment {
        val sel = ObjCRuntime.sel("printSettings")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectionOnly
    open fun isSelectionOnly(): BOOL {
        val sel = ObjCRuntime.sel("isSelectionOnly")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setSelectionOnly(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelectionOnly:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSPrintInfo ─────────────────────────────────────────

// Class<*> method: +[NSPrintInfo setDefaultPrinter:]
fun NSPrintInfo_setDefaultPrinter(printer: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setDefaultPrinter:")
    val cls = ObjCRuntime.getClass("NSPrintInfo")
    ObjCRuntime.msgSend(null, cls, sel, printer)
}

// Class<*> method: +[NSPrintInfo sizeForPaperName:]
fun NSPrintInfo_sizeForPaperName(name: NSPrinterPaperName): NSSize {
    val sel = ObjCRuntime.sel("sizeForPaperName:")
    val cls = ObjCRuntime.getClass("NSPrintInfo")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), cls, sel, name) as NSSize
}


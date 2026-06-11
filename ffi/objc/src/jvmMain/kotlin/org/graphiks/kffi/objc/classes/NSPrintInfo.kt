/**
 * Kotlin/JVM wrapper for Objective-C class: NSPrintInfo
 * Superclass: NSObject
 * Protocols: NSCopying, NSCoding
 */
open class NSPrintInfo(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPrintInfo") }
        
        fun sharedPrintInfo(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedPrintInfo")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun setSharedPrintInfo(sharedPrintInfo: MemorySegment): Unit {
            val sel = ObjCRuntime.sel("setSharedPrintInfo:")
            ObjCRuntime.msgSend(null, _class, sel, sharedPrintInfo)
        }
        
        fun defaultPrinter(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultPrinter")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithDictionary(attributes: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithDictionary:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attributes) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** @return NSMutableDictionary<NSPrintInfoAttributeKey,id> * */
    fun dictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("dictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun setUpPrintOperationDefaultValues(): Unit {
        val sel = ObjCRuntime.sel("setUpPrintOperationDefaultValues")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun PMPrintSession(): MemorySegment {
        val sel = ObjCRuntime.sel("PMPrintSession")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun PMPageFormat(): MemorySegment {
        val sel = ObjCRuntime.sel("PMPageFormat")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun PMPrintSettings(): MemorySegment {
        val sel = ObjCRuntime.sel("PMPrintSettings")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun updateFromPMPageFormat(): Unit {
        val sel = ObjCRuntime.sel("updateFromPMPageFormat")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun updateFromPMPrintSettings(): Unit {
        val sel = ObjCRuntime.sel("updateFromPMPrintSettings")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun takeSettingsFromPDFInfo(inPDFInfo: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("takeSettingsFromPDFInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, inPDFInfo)
    }
    
    // @property sharedPrintInfo
    fun sharedPrintInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedPrintInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSharedPrintInfo(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSharedPrintInfo:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property paperName
    fun paperName(): NSPrinterPaperName {
        val sel = ObjCRuntime.sel("paperName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrinterPaperName
    }
    fun setPaperName(value: NSPrinterPaperName) {
        val sel = ObjCRuntime.sel("setPaperName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property paperSize
    fun paperSize(): NSSize {
        val sel = ObjCRuntime.sel("paperSize")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), ptr, sel) as NSSize
    }
    fun setPaperSize(value: NSSize) {
        val sel = ObjCRuntime.sel("setPaperSize:")
        ObjCRuntime.msgSend(null, ptr, sel, ObjCRuntime.ObjCStructArg(value, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize")))
    }
    
    // @property orientation
    fun orientation(): NSPaperOrientation {
        val sel = ObjCRuntime.sel("orientation")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPaperOrientation
    }
    fun setOrientation(value: NSPaperOrientation) {
        val sel = ObjCRuntime.sel("setOrientation:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property scalingFactor
    fun scalingFactor(): CGFloat {
        val sel = ObjCRuntime.sel("scalingFactor")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setScalingFactor(value: CGFloat) {
        val sel = ObjCRuntime.sel("setScalingFactor:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property leftMargin
    fun leftMargin(): CGFloat {
        val sel = ObjCRuntime.sel("leftMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setLeftMargin(value: CGFloat) {
        val sel = ObjCRuntime.sel("setLeftMargin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rightMargin
    fun rightMargin(): CGFloat {
        val sel = ObjCRuntime.sel("rightMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setRightMargin(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRightMargin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property topMargin
    fun topMargin(): CGFloat {
        val sel = ObjCRuntime.sel("topMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setTopMargin(value: CGFloat) {
        val sel = ObjCRuntime.sel("setTopMargin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property bottomMargin
    fun bottomMargin(): CGFloat {
        val sel = ObjCRuntime.sel("bottomMargin")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setBottomMargin(value: CGFloat) {
        val sel = ObjCRuntime.sel("setBottomMargin:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property horizontallyCentered
    fun isHorizontallyCentered(): BOOL {
        val sel = ObjCRuntime.sel("isHorizontallyCentered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setHorizontallyCentered(value: BOOL) {
        val sel = ObjCRuntime.sel("setHorizontallyCentered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticallyCentered
    fun isVerticallyCentered(): BOOL {
        val sel = ObjCRuntime.sel("isVerticallyCentered")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setVerticallyCentered(value: BOOL) {
        val sel = ObjCRuntime.sel("setVerticallyCentered:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property horizontalPagination
    fun horizontalPagination(): NSPrintingPaginationMode {
        val sel = ObjCRuntime.sel("horizontalPagination")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrintingPaginationMode
    }
    fun setHorizontalPagination(value: NSPrintingPaginationMode) {
        val sel = ObjCRuntime.sel("setHorizontalPagination:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property verticalPagination
    fun verticalPagination(): NSPrintingPaginationMode {
        val sel = ObjCRuntime.sel("verticalPagination")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrintingPaginationMode
    }
    fun setVerticalPagination(value: NSPrintingPaginationMode) {
        val sel = ObjCRuntime.sel("setVerticalPagination:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property jobDisposition
    fun jobDisposition(): NSPrintJobDispositionValue {
        val sel = ObjCRuntime.sel("jobDisposition")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPrintJobDispositionValue
    }
    fun setJobDisposition(value: NSPrintJobDispositionValue) {
        val sel = ObjCRuntime.sel("setJobDisposition:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property printer
    fun printer(): MemorySegment {
        val sel = ObjCRuntime.sel("printer")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPrinter(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPrinter:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property imageablePageBounds
    fun imageablePageBounds(): NSRect {
        val sel = ObjCRuntime.sel("imageablePageBounds")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect"), ptr, sel) as NSRect
    }
    
    // @property localizedPaperName
    fun localizedPaperName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedPaperName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun localizedPaperNameAsString(): String = ObjCRuntime.toJavaString(localizedPaperName())
    
    // @property defaultPrinter
    fun defaultPrinter(): MemorySegment {
        val sel = ObjCRuntime.sel("defaultPrinter")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property printSettings
    /** @return NSMutableDictionary<NSPrintInfoSettingKey,id> * */
    fun printSettings(): MemorySegment {
        val sel = ObjCRuntime.sel("printSettings")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property selectionOnly
    fun isSelectionOnly(): BOOL {
        val sel = ObjCRuntime.sel("isSelectionOnly")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setSelectionOnly(value: BOOL) {
        val sel = ObjCRuntime.sel("setSelectionOnly:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSPrintInfo ─────────────────────────────────────────

// Class method: +[NSPrintInfo setDefaultPrinter:]
fun NSPrintInfo_setDefaultPrinter(printer: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setDefaultPrinter:")
    val cls = ObjCRuntime.getClass("NSPrintInfo")
    ObjCRuntime.msgSend(null, cls, sel, printer)
}

// Class method: +[NSPrintInfo sizeForPaperName:]
fun NSPrintInfo_sizeForPaperName(name: NSPrinterPaperName): NSSize {
    val sel = ObjCRuntime.sel("sizeForPaperName:")
    val cls = ObjCRuntime.getClass("NSPrintInfo")
    return ObjCRuntime.msgSend(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("CGSize"), cls, sel, name) as NSSize
}


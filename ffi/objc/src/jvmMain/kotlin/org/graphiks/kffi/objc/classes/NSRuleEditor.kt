/**
 * Kotlin/JVM wrapper for Objective-C class: NSRuleEditor
 * Superclass: NSControl
 */
open class NSRuleEditor(ptr: MemorySegment) : NSControl(ptr) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRuleEditor") }
        
    }
    
    fun reloadCriteria(): Unit {
        val sel = ObjCRuntime.sel("reloadCriteria")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun reloadPredicate(): Unit {
        val sel = ObjCRuntime.sel("reloadPredicate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun predicateForRow(row: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("predicateForRow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row) as MemorySegment
    }
    
    fun subrowIndexesForRow(rowIndex: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("subrowIndexesForRow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, rowIndex) as MemorySegment
    }
    
    fun criteriaForRow(row: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("criteriaForRow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row) as MemorySegment
    }
    
    fun displayValuesForRow(row: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("displayValuesForRow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row) as MemorySegment
    }
    
    fun rowForDisplayValue(displayValue: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("rowForDisplayValue:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, displayValue) as NSInteger
    }
    
    fun rowTypeForRow(rowIndex: NSInteger): NSRuleEditorRowType {
        val sel = ObjCRuntime.sel("rowTypeForRow:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, rowIndex) as NSRuleEditorRowType
    }
    
    fun parentRowForRow(rowIndex: NSInteger): NSInteger {
        val sel = ObjCRuntime.sel("parentRowForRow:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, rowIndex) as NSInteger
    }
    
    fun addRow(sender: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addRow:")
        ObjCRuntime.msgSend(null, ptr, sel, sender)
    }
    
    fun insertRowAtIndex_withType_asSubrowOfRow_animate(rowIndex: NSInteger, rowType: NSRuleEditorRowType, parentRow: NSInteger, shouldAnimate: BOOL): Unit {
        val sel = ObjCRuntime.sel("insertRowAtIndex:withType:asSubrowOfRow:animate:")
        ObjCRuntime.msgSend(null, ptr, sel, rowIndex, rowType, parentRow, shouldAnimate)
    }
    
    fun setCriteria_andDisplayValues_forRowAtIndex(criteria: MemorySegment, values: MemorySegment, rowIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setCriteria:andDisplayValues:forRowAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, criteria, values, rowIndex)
    }
    
    fun removeRowAtIndex(rowIndex: NSInteger): Unit {
        val sel = ObjCRuntime.sel("removeRowAtIndex:")
        ObjCRuntime.msgSend(null, ptr, sel, rowIndex)
    }
    
    fun removeRowsAtIndexes_includeSubrows(rowIndexes: MemorySegment, includeSubrows: BOOL): Unit {
        val sel = ObjCRuntime.sel("removeRowsAtIndexes:includeSubrows:")
        ObjCRuntime.msgSend(null, ptr, sel, rowIndexes, includeSubrows)
    }
    
    fun selectRowIndexes_byExtendingSelection(indexes: MemorySegment, extend: BOOL): Unit {
        val sel = ObjCRuntime.sel("selectRowIndexes:byExtendingSelection:")
        ObjCRuntime.msgSend(null, ptr, sel, indexes, extend)
    }
    
    // @property delegate
    /** @return id<NSRuleEditorDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property formattingStringsFilename
    fun formattingStringsFilename(): MemorySegment {
        val sel = ObjCRuntime.sel("formattingStringsFilename")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFormattingStringsFilename(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFormattingStringsFilename:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun formattingStringsFilenameAsString(): String = ObjCRuntime.toJavaString(formattingStringsFilename())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setFormattingStringsFilename(value: String) = setFormattingStringsFilename(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property formattingDictionary
    /** @return NSDictionary<NSString *,NSString *> * */
    fun formattingDictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("formattingDictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFormattingDictionary(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFormattingDictionary:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property nestingMode
    fun nestingMode(): NSRuleEditorNestingMode {
        val sel = ObjCRuntime.sel("nestingMode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSRuleEditorNestingMode
    }
    fun setNestingMode(value: NSRuleEditorNestingMode) {
        val sel = ObjCRuntime.sel("setNestingMode:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rowHeight
    fun rowHeight(): CGFloat {
        val sel = ObjCRuntime.sel("rowHeight")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    fun setRowHeight(value: CGFloat) {
        val sel = ObjCRuntime.sel("setRowHeight:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property editable
    fun isEditable(): BOOL {
        val sel = ObjCRuntime.sel("isEditable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setEditable(value: BOOL) {
        val sel = ObjCRuntime.sel("setEditable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canRemoveAllRows
    fun canRemoveAllRows(): BOOL {
        val sel = ObjCRuntime.sel("canRemoveAllRows")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setCanRemoveAllRows(value: BOOL) {
        val sel = ObjCRuntime.sel("setCanRemoveAllRows:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property predicate
    fun predicate(): MemorySegment {
        val sel = ObjCRuntime.sel("predicate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property numberOfRows
    fun numberOfRows(): NSInteger {
        val sel = ObjCRuntime.sel("numberOfRows")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property selectedRowIndexes
    fun selectedRowIndexes(): MemorySegment {
        val sel = ObjCRuntime.sel("selectedRowIndexes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property rowClass
    fun rowClass(): Class {
        val sel = ObjCRuntime.sel("rowClass")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
    }
    fun setRowClass(value: Class) {
        val sel = ObjCRuntime.sel("setRowClass:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rowTypeKeyPath
    fun rowTypeKeyPath(): MemorySegment {
        val sel = ObjCRuntime.sel("rowTypeKeyPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRowTypeKeyPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRowTypeKeyPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun rowTypeKeyPathAsString(): String = ObjCRuntime.toJavaString(rowTypeKeyPath())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setRowTypeKeyPath(value: String) = setRowTypeKeyPath(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property subrowsKeyPath
    fun subrowsKeyPath(): MemorySegment {
        val sel = ObjCRuntime.sel("subrowsKeyPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSubrowsKeyPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubrowsKeyPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun subrowsKeyPathAsString(): String = ObjCRuntime.toJavaString(subrowsKeyPath())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSubrowsKeyPath(value: String) = setSubrowsKeyPath(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property criteriaKeyPath
    fun criteriaKeyPath(): MemorySegment {
        val sel = ObjCRuntime.sel("criteriaKeyPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCriteriaKeyPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCriteriaKeyPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun criteriaKeyPathAsString(): String = ObjCRuntime.toJavaString(criteriaKeyPath())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setCriteriaKeyPath(value: String) = setCriteriaKeyPath(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property displayValuesKeyPath
    fun displayValuesKeyPath(): MemorySegment {
        val sel = ObjCRuntime.sel("displayValuesKeyPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDisplayValuesKeyPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDisplayValuesKeyPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun displayValuesKeyPathAsString(): String = ObjCRuntime.toJavaString(displayValuesKeyPath())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setDisplayValuesKeyPath(value: String) = setDisplayValuesKeyPath(ObjCRuntime.newNSString(Arena.global(), value))
    
}


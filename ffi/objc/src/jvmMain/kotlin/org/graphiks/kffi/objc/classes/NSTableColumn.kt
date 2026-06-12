package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSTableColumn
 * Superclass: NSObject
 * Protocols: NSCoding, NSUserInterfaceItemIdentification
 */
open class NSTableColumn(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTableColumn") }
        
    }
    
    open fun initWithIdentifier(identifier: NSUserInterfaceItemIdentifier): MemorySegment {
        val sel = ObjCRuntime.sel("initWithIdentifier:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identifier) as MemorySegment
    }
    
    open fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    open fun sizeToFit(): Unit {
        val sel = ObjCRuntime.sel("sizeToFit")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property identifier
    open fun identifier(): NSUserInterfaceItemIdentifier {
        val sel = ObjCRuntime.sel("identifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSUserInterfaceItemIdentifier
    }
    open fun setIdentifier(value: NSUserInterfaceItemIdentifier) {
        val sel = ObjCRuntime.sel("setIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property tableView
    open fun tableView(): MemorySegment {
        val sel = ObjCRuntime.sel("tableView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTableView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTableView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property width
    open fun width(): CGFloat {
        val sel = ObjCRuntime.sel("width")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property minWidth
    open fun minWidth(): CGFloat {
        val sel = ObjCRuntime.sel("minWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setMinWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMinWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property maxWidth
    open fun maxWidth(): CGFloat {
        val sel = ObjCRuntime.sel("maxWidth")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as CGFloat
    }
    open fun setMaxWidth(value: CGFloat) {
        val sel = ObjCRuntime.sel("setMaxWidth:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property title
    open fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setTitle(value: String) = setTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property headerCell
    open fun headerCell(): MemorySegment {
        val sel = ObjCRuntime.sel("headerCell")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setHeaderCell(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHeaderCell:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property editable
    open fun isEditable(): BOOL {
        val sel = ObjCRuntime.sel("isEditable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setEditable(value: BOOL) {
        val sel = ObjCRuntime.sel("setEditable:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property sortDescriptorPrototype
    open fun sortDescriptorPrototype(): MemorySegment {
        val sel = ObjCRuntime.sel("sortDescriptorPrototype")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setSortDescriptorPrototype(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSortDescriptorPrototype:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property resizingMask
    open fun resizingMask(): NSTableColumnResizingOptions {
        val sel = ObjCRuntime.sel("resizingMask")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTableColumnResizingOptions
    }
    open fun setResizingMask(value: NSTableColumnResizingOptions) {
        val sel = ObjCRuntime.sel("setResizingMask:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property headerToolTip
    open fun headerToolTip(): MemorySegment {
        val sel = ObjCRuntime.sel("headerToolTip")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setHeaderToolTip(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHeaderToolTip:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun headerToolTipAsString(): String = ObjCRuntime.toJavaString(headerToolTip())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setHeaderToolTip(value: String) = setHeaderToolTip(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property hidden
    open fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setHidden(value: BOOL) {
        val sel = ObjCRuntime.sel("setHidden:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSTableColumn ─────────────────────────────────────────

fun NSTableColumn.setResizable(flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("setResizable:")
    ObjCRuntime.msgSend(null, ptr, sel, flag)
}

fun NSTableColumn.isResizable(): BOOL {
    val sel = ObjCRuntime.sel("isResizable")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSTableColumn.dataCellForRow(row: NSInteger): MemorySegment {
    val sel = ObjCRuntime.sel("dataCellForRow:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, row) as MemorySegment
}

fun NSTableColumn.dataCell(): MemorySegment {
    val sel = ObjCRuntime.sel("dataCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTableColumn.setDataCell(dataCell: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setDataCell:")
    ObjCRuntime.msgSend(null, ptr, sel, dataCell)
}

// @property dataCell
    val sel = ObjCRuntime.sel("dataCell")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
    val sel = ObjCRuntime.sel("setDataCell:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}


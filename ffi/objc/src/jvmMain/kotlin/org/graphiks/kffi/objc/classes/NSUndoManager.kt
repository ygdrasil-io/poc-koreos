/**
 * Kotlin/JVM wrapper for Objective-C class: NSUndoManager
 * Superclass: NSObject
 */
open class NSUndoManager(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSUndoManager") }
        
    }
    
    fun beginUndoGrouping(): Unit {
        val sel = ObjCRuntime.sel("beginUndoGrouping")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun endUndoGrouping(): Unit {
        val sel = ObjCRuntime.sel("endUndoGrouping")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun disableUndoRegistration(): Unit {
        val sel = ObjCRuntime.sel("disableUndoRegistration")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun enableUndoRegistration(): Unit {
        val sel = ObjCRuntime.sel("enableUndoRegistration")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun undo(): Unit {
        val sel = ObjCRuntime.sel("undo")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun redo(): Unit {
        val sel = ObjCRuntime.sel("redo")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun undoNestedGroup(): Unit {
        val sel = ObjCRuntime.sel("undoNestedGroup")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun removeAllActions(): Unit {
        val sel = ObjCRuntime.sel("removeAllActions")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun removeAllActionsWithTarget(target: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeAllActionsWithTarget:")
        ObjCRuntime.msgSend(null, ptr, sel, target)
    }
    
    fun registerUndoWithTarget_selector_object(target: MemorySegment, selector: MemorySegment, `object`: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerUndoWithTarget:selector:object:")
        ObjCRuntime.msgSend(null, ptr, sel, target, selector, `object`)
    }
    
    fun prepareWithInvocationTarget(target: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("prepareWithInvocationTarget:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, target) as MemorySegment
    }
    
    fun registerUndoWithTarget_handler(target: MemorySegment, undoHandler: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("registerUndoWithTarget:handler:")
        ObjCRuntime.msgSend(null, ptr, sel, target, undoHandler)
    }
    
    fun setActionIsDiscardable(discardable: BOOL): Unit {
        val sel = ObjCRuntime.sel("setActionIsDiscardable:")
        ObjCRuntime.msgSend(null, ptr, sel, discardable)
    }
    
    fun setActionName(actionName: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setActionName:")
        ObjCRuntime.msgSend(null, ptr, sel, actionName)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setActionName(actionName: String): Unit = setActionName(ObjCRuntime.newNSString(Arena.global(), actionName))
    
    fun undoActionUserInfoValueForKey(key: NSUndoManagerUserInfoKey): MemorySegment {
        val sel = ObjCRuntime.sel("undoActionUserInfoValueForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    fun redoActionUserInfoValueForKey(key: NSUndoManagerUserInfoKey): MemorySegment {
        val sel = ObjCRuntime.sel("redoActionUserInfoValueForKey:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
    }
    
    fun setActionUserInfoValue_forKey(info: MemorySegment, key: NSUndoManagerUserInfoKey): Unit {
        val sel = ObjCRuntime.sel("setActionUserInfoValue:forKey:")
        ObjCRuntime.msgSend(null, ptr, sel, info, key)
    }
    
    fun undoMenuTitleForUndoActionName(actionName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("undoMenuTitleForUndoActionName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, actionName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun undoMenuTitleForUndoActionNameAsString(actionName: MemorySegment): String = ObjCRuntime.toJavaString(undoMenuTitleForUndoActionName(actionName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun undoMenuTitleForUndoActionName(actionName: String): MemorySegment = undoMenuTitleForUndoActionName(ObjCRuntime.newNSString(Arena.global(), actionName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun undoMenuTitleForUndoActionNameAsString(actionName: String): String = ObjCRuntime.toJavaString(undoMenuTitleForUndoActionName(ObjCRuntime.newNSString(Arena.global(), actionName)))
    
    fun redoMenuTitleForUndoActionName(actionName: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("redoMenuTitleForUndoActionName:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, actionName) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun redoMenuTitleForUndoActionNameAsString(actionName: MemorySegment): String = ObjCRuntime.toJavaString(redoMenuTitleForUndoActionName(actionName))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun redoMenuTitleForUndoActionName(actionName: String): MemorySegment = redoMenuTitleForUndoActionName(ObjCRuntime.newNSString(Arena.global(), actionName))
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun redoMenuTitleForUndoActionNameAsString(actionName: String): String = ObjCRuntime.toJavaString(redoMenuTitleForUndoActionName(ObjCRuntime.newNSString(Arena.global(), actionName)))
    
    // @property groupingLevel
    fun groupingLevel(): NSInteger {
        val sel = ObjCRuntime.sel("groupingLevel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property undoRegistrationEnabled
    fun isUndoRegistrationEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isUndoRegistrationEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property groupsByEvent
    fun groupsByEvent(): BOOL {
        val sel = ObjCRuntime.sel("groupsByEvent")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setGroupsByEvent(value: BOOL) {
        val sel = ObjCRuntime.sel("setGroupsByEvent:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property levelsOfUndo
    fun levelsOfUndo(): NSUInteger {
        val sel = ObjCRuntime.sel("levelsOfUndo")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    fun setLevelsOfUndo(value: NSUInteger) {
        val sel = ObjCRuntime.sel("setLevelsOfUndo:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property runLoopModes
    /** @return NSArray<NSRunLoopMode> * */
    fun runLoopModes(): MemorySegment {
        val sel = ObjCRuntime.sel("runLoopModes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRunLoopModes(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRunLoopModes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property canUndo
    fun canUndo(): BOOL {
        val sel = ObjCRuntime.sel("canUndo")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property canRedo
    fun canRedo(): BOOL {
        val sel = ObjCRuntime.sel("canRedo")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property undoCount
    fun undoCount(): NSUInteger {
        val sel = ObjCRuntime.sel("undoCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property redoCount
    fun redoCount(): NSUInteger {
        val sel = ObjCRuntime.sel("redoCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property undoing
    fun isUndoing(): BOOL {
        val sel = ObjCRuntime.sel("isUndoing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property redoing
    fun isRedoing(): BOOL {
        val sel = ObjCRuntime.sel("isRedoing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property undoActionIsDiscardable
    fun undoActionIsDiscardable(): BOOL {
        val sel = ObjCRuntime.sel("undoActionIsDiscardable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property redoActionIsDiscardable
    fun redoActionIsDiscardable(): BOOL {
        val sel = ObjCRuntime.sel("redoActionIsDiscardable")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property undoActionName
    fun undoActionName(): MemorySegment {
        val sel = ObjCRuntime.sel("undoActionName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun undoActionNameAsString(): String = ObjCRuntime.toJavaString(undoActionName())
    
    // @property redoActionName
    fun redoActionName(): MemorySegment {
        val sel = ObjCRuntime.sel("redoActionName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun redoActionNameAsString(): String = ObjCRuntime.toJavaString(redoActionName())
    
    // @property undoMenuItemTitle
    fun undoMenuItemTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("undoMenuItemTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun undoMenuItemTitleAsString(): String = ObjCRuntime.toJavaString(undoMenuItemTitle())
    
    // @property redoMenuItemTitle
    fun redoMenuItemTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("redoMenuItemTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun redoMenuItemTitleAsString(): String = ObjCRuntime.toJavaString(redoMenuItemTitle())
    
}


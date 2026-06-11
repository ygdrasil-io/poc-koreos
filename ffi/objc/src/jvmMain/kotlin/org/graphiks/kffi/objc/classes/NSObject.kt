// ── Category: NSCoderMethods on NSObject ─────────────────────────────────────────

fun NSObject.replacementObjectForCoder(coder: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("replacementObjectForCoder:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
}

fun NSObject.awakeAfterUsingCoder(coder: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("awakeAfterUsingCoder:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
}

fun NSObject.classForCoder(): Class {
    val sel = ObjCRuntime.sel("classForCoder")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
}

// Class method: +[NSObject version]
fun NSObject_version(): NSInteger {
    val sel = ObjCRuntime.sel("version")
    val cls = ObjCRuntime.getClass("NSObject")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, cls, sel) as NSInteger
}

// Class method: +[NSObject setVersion:]
fun NSObject_setVersion(aVersion: NSInteger): Unit {
    val sel = ObjCRuntime.sel("setVersion:")
    val cls = ObjCRuntime.getClass("NSObject")
    ObjCRuntime.msgSend(null, cls, sel, aVersion)
}

// @property classForCoder
fun NSObject.classForCoder(): Class {
    val sel = ObjCRuntime.sel("classForCoder")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
}

// ── Category: NSDeprecatedMethods on NSObject ─────────────────────────────────────────

// Class method: +[NSObject poseAsClass:]
fun NSObject_poseAsClass(aClass: Class): Unit {
    val sel = ObjCRuntime.sel("poseAsClass:")
    val cls = ObjCRuntime.getClass("NSObject")
    ObjCRuntime.msgSend(null, cls, sel, aClass)
}

// ── Category: NSDiscardableContentProxy on NSObject ─────────────────────────────────────────

fun NSObject.autoContentAccessingProxy(): MemorySegment {
    val sel = ObjCRuntime.sel("autoContentAccessingProxy")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property autoContentAccessingProxy
fun NSObject.autoContentAccessingProxy(): MemorySegment {
    val sel = ObjCRuntime.sel("autoContentAccessingProxy")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSErrorRecoveryAttempting on NSObject ─────────────────────────────────────────

fun NSObject.attemptRecoveryFromError_optionIndex_delegate_didRecoverSelector_contextInfo(error: MemorySegment, recoveryOptionIndex: NSUInteger, delegate: MemorySegment, didRecoverSelector: MemorySegment, contextInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("attemptRecoveryFromError:optionIndex:delegate:didRecoverSelector:contextInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, error, recoveryOptionIndex, delegate, didRecoverSelector, contextInfo)
}

fun NSObject.attemptRecoveryFromError_optionIndex(error: MemorySegment, recoveryOptionIndex: NSUInteger): BOOL {
    val sel = ObjCRuntime.sel("attemptRecoveryFromError:optionIndex:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error, recoveryOptionIndex) as BOOL
}

// ── Category: NSDelayedPerforming on NSObject ─────────────────────────────────────────

fun NSObject.performSelector_withObject_afterDelay_inModes(aSelector: MemorySegment, anArgument: MemorySegment, delay: NSTimeInterval, modes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performSelector:withObject:afterDelay:inModes:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, anArgument, delay, modes)
}

fun NSObject.performSelector_withObject_afterDelay(aSelector: MemorySegment, anArgument: MemorySegment, delay: NSTimeInterval): Unit {
    val sel = ObjCRuntime.sel("performSelector:withObject:afterDelay:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, anArgument, delay)
}

// Class method: +[NSObject cancelPreviousPerformRequestsWithTarget:selector:object:]
fun NSObject_cancelPreviousPerformRequestsWithTarget_selector_object(aTarget: MemorySegment, aSelector: MemorySegment, anArgument: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("cancelPreviousPerformRequestsWithTarget:selector:object:")
    val cls = ObjCRuntime.getClass("NSObject")
    ObjCRuntime.msgSend(null, cls, sel, aTarget, aSelector, anArgument)
}

// Class method: +[NSObject cancelPreviousPerformRequestsWithTarget:]
fun NSObject_cancelPreviousPerformRequestsWithTarget(aTarget: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("cancelPreviousPerformRequestsWithTarget:")
    val cls = ObjCRuntime.getClass("NSObject")
    ObjCRuntime.msgSend(null, cls, sel, aTarget)
}

// ── Category: NSURLClient on NSObject ─────────────────────────────────────────

fun NSObject.URL_resourceDataDidBecomeAvailable(sender: MemorySegment, newBytes: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("URL:resourceDataDidBecomeAvailable:")
    ObjCRuntime.msgSend(null, ptr, sel, sender, newBytes)
}

fun NSObject.URLResourceDidFinishLoading(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("URLResourceDidFinishLoading:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSObject.URLResourceDidCancelLoading(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("URLResourceDidCancelLoading:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

fun NSObject.URL_resourceDidFailLoadingWithReason(sender: MemorySegment, reason: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("URL:resourceDidFailLoadingWithReason:")
    ObjCRuntime.msgSend(null, ptr, sel, sender, reason)
}

// ── Category: NSCopyLinkMoveHandler on NSObject ─────────────────────────────────────────

fun NSObject.fileManager_shouldProceedAfterError(fm: MemorySegment, errorInfo: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("fileManager:shouldProceedAfterError:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, fm, errorInfo) as BOOL
}

fun NSObject.fileManager_willProcessPath(fm: MemorySegment, path: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("fileManager:willProcessPath:")
    ObjCRuntime.msgSend(null, ptr, sel, fm, path)
}

// ── Category: NSKeyValueCoding on NSObject ─────────────────────────────────────────

fun NSObject.valueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSObject.setValue_forKey(value: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setValue:forKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, key)
}

fun NSObject.validateValue_forKey_error(ioValue: MemorySegment, inKey: MemorySegment, outError: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("validateValue:forKey:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ioValue, inKey, outError) as BOOL
}

fun NSObject.mutableArrayValueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("mutableArrayValueForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSObject.mutableOrderedSetValueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("mutableOrderedSetValueForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSObject.mutableSetValueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("mutableSetValueForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSObject.valueForKeyPath(keyPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueForKeyPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keyPath) as MemorySegment
}

fun NSObject.setValue_forKeyPath(value: MemorySegment, keyPath: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setValue:forKeyPath:")
    ObjCRuntime.msgSend(null, ptr, sel, value, keyPath)
}

fun NSObject.validateValue_forKeyPath_error(ioValue: MemorySegment, inKeyPath: MemorySegment, outError: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("validateValue:forKeyPath:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ioValue, inKeyPath, outError) as BOOL
}

fun NSObject.mutableArrayValueForKeyPath(keyPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("mutableArrayValueForKeyPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keyPath) as MemorySegment
}

fun NSObject.mutableOrderedSetValueForKeyPath(keyPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("mutableOrderedSetValueForKeyPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keyPath) as MemorySegment
}

fun NSObject.mutableSetValueForKeyPath(keyPath: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("mutableSetValueForKeyPath:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keyPath) as MemorySegment
}

fun NSObject.valueForUndefinedKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueForUndefinedKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSObject.setValue_forUndefinedKey(value: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setValue:forUndefinedKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, key)
}

fun NSObject.setNilValueForKey(key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setNilValueForKey:")
    ObjCRuntime.msgSend(null, ptr, sel, key)
}

/** @return NSDictionary<NSString *,id> * */
fun NSObject.dictionaryWithValuesForKeys(keys: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("dictionaryWithValuesForKeys:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keys) as MemorySegment
}

fun NSObject.setValuesForKeysWithDictionary(keyedValues: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setValuesForKeysWithDictionary:")
    ObjCRuntime.msgSend(null, ptr, sel, keyedValues)
}

// Class method: +[NSObject accessInstanceVariablesDirectly]
fun NSObject_accessInstanceVariablesDirectly(): BOOL {
    val sel = ObjCRuntime.sel("accessInstanceVariablesDirectly")
    val cls = ObjCRuntime.getClass("NSObject")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel) as BOOL
}

// @property accessInstanceVariablesDirectly
fun NSObject.accessInstanceVariablesDirectly(): BOOL {
    val sel = ObjCRuntime.sel("accessInstanceVariablesDirectly")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSDeprecatedKeyValueCoding on NSObject ─────────────────────────────────────────

fun NSObject.storedValueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("storedValueForKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSObject.takeStoredValue_forKey(value: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("takeStoredValue:forKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, key)
}

fun NSObject.takeValue_forKey(value: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("takeValue:forKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, key)
}

fun NSObject.takeValue_forKeyPath(value: MemorySegment, keyPath: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("takeValue:forKeyPath:")
    ObjCRuntime.msgSend(null, ptr, sel, value, keyPath)
}

fun NSObject.handleQueryWithUnboundKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("handleQueryWithUnboundKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, key) as MemorySegment
}

fun NSObject.handleTakeValue_forUnboundKey(value: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("handleTakeValue:forUnboundKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, key)
}

fun NSObject.unableToSetNilForKey(key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("unableToSetNilForKey:")
    ObjCRuntime.msgSend(null, ptr, sel, key)
}

fun NSObject.valuesForKeys(keys: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valuesForKeys:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, keys) as MemorySegment
}

fun NSObject.takeValuesFromDictionary(properties: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("takeValuesFromDictionary:")
    ObjCRuntime.msgSend(null, ptr, sel, properties)
}

// Class method: +[NSObject useStoredAccessor]
fun NSObject_useStoredAccessor(): BOOL {
    val sel = ObjCRuntime.sel("useStoredAccessor")
    val cls = ObjCRuntime.getClass("NSObject")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel) as BOOL
}

// ── Category: NSKeyValueObserving on NSObject ─────────────────────────────────────────

fun NSObject.observeValueForKeyPath_ofObject_change_context(keyPath: MemorySegment, `object`: MemorySegment, change: MemorySegment, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("observeValueForKeyPath:ofObject:change:context:")
    ObjCRuntime.msgSend(null, ptr, sel, keyPath, `object`, change, context)
}

// ── Category: NSKeyValueObserverRegistration on NSObject ─────────────────────────────────────────

fun NSObject.addObserver_forKeyPath_options_context(observer: MemorySegment, keyPath: MemorySegment, options: NSKeyValueObservingOptions, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("addObserver:forKeyPath:options:context:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath, options, context)
}

fun NSObject.removeObserver_forKeyPath_context(observer: MemorySegment, keyPath: MemorySegment, context: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObserver:forKeyPath:context:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath, context)
}

fun NSObject.removeObserver_forKeyPath(observer: MemorySegment, keyPath: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeObserver:forKeyPath:")
    ObjCRuntime.msgSend(null, ptr, sel, observer, keyPath)
}

// ── Category: NSKeyValueObserverNotification on NSObject ─────────────────────────────────────────

fun NSObject.willChangeValueForKey(key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("willChangeValueForKey:")
    ObjCRuntime.msgSend(null, ptr, sel, key)
}

fun NSObject.didChangeValueForKey(key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("didChangeValueForKey:")
    ObjCRuntime.msgSend(null, ptr, sel, key)
}

fun NSObject.willChange_valuesAtIndexes_forKey(changeKind: NSKeyValueChange, indexes: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("willChange:valuesAtIndexes:forKey:")
    ObjCRuntime.msgSend(null, ptr, sel, changeKind, indexes, key)
}

fun NSObject.didChange_valuesAtIndexes_forKey(changeKind: NSKeyValueChange, indexes: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("didChange:valuesAtIndexes:forKey:")
    ObjCRuntime.msgSend(null, ptr, sel, changeKind, indexes, key)
}

fun NSObject.willChangeValueForKey_withSetMutation_usingObjects(key: MemorySegment, mutationKind: NSKeyValueSetMutationKind, objects: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("willChangeValueForKey:withSetMutation:usingObjects:")
    ObjCRuntime.msgSend(null, ptr, sel, key, mutationKind, objects)
}

fun NSObject.didChangeValueForKey_withSetMutation_usingObjects(key: MemorySegment, mutationKind: NSKeyValueSetMutationKind, objects: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("didChangeValueForKey:withSetMutation:usingObjects:")
    ObjCRuntime.msgSend(null, ptr, sel, key, mutationKind, objects)
}

// ── Category: NSKeyValueObservingCustomization on NSObject ─────────────────────────────────────────

fun NSObject.observationInfo(): MemorySegment {
    val sel = ObjCRuntime.sel("observationInfo")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObject.setObservationInfo(observationInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setObservationInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, observationInfo)
}

// Class method: +[NSObject keyPathsForValuesAffectingValueForKey:]
fun NSObject_keyPathsForValuesAffectingValueForKey(key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("keyPathsForValuesAffectingValueForKey:")
    val cls = ObjCRuntime.getClass("NSObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, key) as MemorySegment
}

// Class method: +[NSObject automaticallyNotifiesObserversForKey:]
fun NSObject_automaticallyNotifiesObserversForKey(key: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("automaticallyNotifiesObserversForKey:")
    val cls = ObjCRuntime.getClass("NSObject")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, cls, sel, key) as BOOL
}

// @property observationInfo
fun NSObject.observationInfo(): MemorySegment {
    val sel = ObjCRuntime.sel("observationInfo")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSObject.setObservationInfo(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setObservationInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSDeprecatedKeyValueObservingCustomization on NSObject ─────────────────────────────────────────

// Class method: +[NSObject setKeys:triggerChangeNotificationsForDependentKey:]
fun NSObject_setKeys_triggerChangeNotificationsForDependentKey(keys: MemorySegment, dependentKey: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setKeys:triggerChangeNotificationsForDependentKey:")
    val cls = ObjCRuntime.getClass("NSObject")
    ObjCRuntime.msgSend(null, cls, sel, keys, dependentKey)
}

// ── Category: NSKeyValueSharedObserverRegistration on NSObject ─────────────────────────────────────────

fun NSObject.setSharedObservers(sharedObservers: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setSharedObservers:")
    ObjCRuntime.msgSend(null, ptr, sel, sharedObservers)
}

// ── Category: NSKeyedArchiverObjectSubstitution on NSObject ─────────────────────────────────────────

fun NSObject.replacementObjectForKeyedArchiver(archiver: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("replacementObjectForKeyedArchiver:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, archiver) as MemorySegment
}

fun NSObject.classForKeyedArchiver(): Class {
    val sel = ObjCRuntime.sel("classForKeyedArchiver")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
}

// Class method: +[NSObject classFallbacksForKeyedArchiver]
fun NSObject_classFallbacksForKeyedArchiver(): MemorySegment {
    val sel = ObjCRuntime.sel("classFallbacksForKeyedArchiver")
    val cls = ObjCRuntime.getClass("NSObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// @property classForKeyedArchiver
fun NSObject.classForKeyedArchiver(): Class {
    val sel = ObjCRuntime.sel("classForKeyedArchiver")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
}

// ── Category: NSKeyedUnarchiverObjectSubstitution on NSObject ─────────────────────────────────────────

// Class method: +[NSObject classForKeyedUnarchiver]
fun NSObject_classForKeyedUnarchiver(): Class {
    val sel = ObjCRuntime.sel("classForKeyedUnarchiver")
    val cls = ObjCRuntime.getClass("NSObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as Class
}

// ── Category: NSThreadPerformAdditions on NSObject ─────────────────────────────────────────

fun NSObject.performSelectorOnMainThread_withObject_waitUntilDone_modes(aSelector: MemorySegment, arg: MemorySegment, wait: BOOL, array: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performSelectorOnMainThread:withObject:waitUntilDone:modes:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, arg, wait, array)
}

fun NSObject.performSelectorOnMainThread_withObject_waitUntilDone(aSelector: MemorySegment, arg: MemorySegment, wait: BOOL): Unit {
    val sel = ObjCRuntime.sel("performSelectorOnMainThread:withObject:waitUntilDone:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, arg, wait)
}

fun NSObject.performSelector_onThread_withObject_waitUntilDone_modes(aSelector: MemorySegment, thr: MemorySegment, arg: MemorySegment, wait: BOOL, array: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performSelector:onThread:withObject:waitUntilDone:modes:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, thr, arg, wait, array)
}

fun NSObject.performSelector_onThread_withObject_waitUntilDone(aSelector: MemorySegment, thr: MemorySegment, arg: MemorySegment, wait: BOOL): Unit {
    val sel = ObjCRuntime.sel("performSelector:onThread:withObject:waitUntilDone:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, thr, arg, wait)
}

fun NSObject.performSelectorInBackground_withObject(aSelector: MemorySegment, arg: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performSelectorInBackground:withObject:")
    ObjCRuntime.msgSend(null, ptr, sel, aSelector, arg)
}

// ── Category: NSArchiverCallback on NSObject ─────────────────────────────────────────

fun NSObject.replacementObjectForArchiver(archiver: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("replacementObjectForArchiver:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, archiver) as MemorySegment
}

fun NSObject.classForArchiver(): Class {
    val sel = ObjCRuntime.sel("classForArchiver")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
}

// @property classForArchiver
fun NSObject.classForArchiver(): Class {
    val sel = ObjCRuntime.sel("classForArchiver")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
}

// ── Category: NSDistributedObjects on NSObject ─────────────────────────────────────────

fun NSObject.replacementObjectForPortCoder(coder: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("replacementObjectForPortCoder:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
}

fun NSObject.classForPortCoder(): Class {
    val sel = ObjCRuntime.sel("classForPortCoder")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
}

// @property classForPortCoder
fun NSObject.classForPortCoder(): Class {
    val sel = ObjCRuntime.sel("classForPortCoder")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as Class
}

// ── Category: NSClassDescriptionPrimitives on NSObject ─────────────────────────────────────────

fun NSObject.inverseForRelationshipKey(relationshipKey: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("inverseForRelationshipKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, relationshipKey) as MemorySegment
}

fun NSObject.classDescription(): MemorySegment {
    val sel = ObjCRuntime.sel("classDescription")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSString *> * */
fun NSObject.attributeKeys(): MemorySegment {
    val sel = ObjCRuntime.sel("attributeKeys")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSString *> * */
fun NSObject.toOneRelationshipKeys(): MemorySegment {
    val sel = ObjCRuntime.sel("toOneRelationshipKeys")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

/** @return NSArray<NSString *> * */
fun NSObject.toManyRelationshipKeys(): MemorySegment {
    val sel = ObjCRuntime.sel("toManyRelationshipKeys")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property classDescription
fun NSObject.classDescription(): MemorySegment {
    val sel = ObjCRuntime.sel("classDescription")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property attributeKeys
/** @return NSArray<NSString *> * */
fun NSObject.attributeKeys(): MemorySegment {
    val sel = ObjCRuntime.sel("attributeKeys")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property toOneRelationshipKeys
/** @return NSArray<NSString *> * */
fun NSObject.toOneRelationshipKeys(): MemorySegment {
    val sel = ObjCRuntime.sel("toOneRelationshipKeys")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property toManyRelationshipKeys
/** @return NSArray<NSString *> * */
fun NSObject.toManyRelationshipKeys(): MemorySegment {
    val sel = ObjCRuntime.sel("toManyRelationshipKeys")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSScripting on NSObject ─────────────────────────────────────────

fun NSObject.scriptingValueForSpecifier(objectSpecifier: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("scriptingValueForSpecifier:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, objectSpecifier) as MemorySegment
}

fun NSObject.copyScriptingValue_forKey_withProperties(value: MemorySegment, key: MemorySegment, properties: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("copyScriptingValue:forKey:withProperties:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value, key, properties) as MemorySegment
}

fun NSObject.newScriptingObjectOfClass_forValueForKey_withContentsValue_properties(objectClass: Class, key: MemorySegment, contentsValue: MemorySegment, properties: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("newScriptingObjectOfClass:forValueForKey:withContentsValue:properties:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, objectClass, key, contentsValue, properties) as MemorySegment
}

/** @return NSDictionary<NSString *,id> * */
fun NSObject.scriptingProperties(): MemorySegment {
    val sel = ObjCRuntime.sel("scriptingProperties")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObject.setScriptingProperties(scriptingProperties: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setScriptingProperties:")
    ObjCRuntime.msgSend(null, ptr, sel, scriptingProperties)
}

// @property scriptingProperties
/** @return NSDictionary<NSString *,id> * */
fun NSObject.scriptingProperties(): MemorySegment {
    val sel = ObjCRuntime.sel("scriptingProperties")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSObject.setScriptingProperties(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setScriptingProperties:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// ── Category: NSScriptClassDescription on NSObject ─────────────────────────────────────────

fun NSObject.classCode(): FourCharCode {
    val sel = ObjCRuntime.sel("classCode")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as FourCharCode
}

fun NSObject.className(): MemorySegment {
    val sel = ObjCRuntime.sel("className")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property classCode
fun NSObject.classCode(): FourCharCode {
    val sel = ObjCRuntime.sel("classCode")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as FourCharCode
}

// @property className
fun NSObject.className(): MemorySegment {
    val sel = ObjCRuntime.sel("className")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSScriptKeyValueCoding on NSObject ─────────────────────────────────────────

fun NSObject.valueAtIndex_inPropertyWithKey(index: NSUInteger, key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueAtIndex:inPropertyWithKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index, key) as MemorySegment
}

fun NSObject.valueWithName_inPropertyWithKey(name: MemorySegment, key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueWithName:inPropertyWithKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, name, key) as MemorySegment
}

fun NSObject.valueWithUniqueID_inPropertyWithKey(uniqueID: MemorySegment, key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("valueWithUniqueID:inPropertyWithKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, uniqueID, key) as MemorySegment
}

fun NSObject.insertValue_atIndex_inPropertyWithKey(value: MemorySegment, index: NSUInteger, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("insertValue:atIndex:inPropertyWithKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, index, key)
}

fun NSObject.removeValueAtIndex_fromPropertyWithKey(index: NSUInteger, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("removeValueAtIndex:fromPropertyWithKey:")
    ObjCRuntime.msgSend(null, ptr, sel, index, key)
}

fun NSObject.replaceValueAtIndex_inPropertyWithKey_withValue(index: NSUInteger, key: MemorySegment, value: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("replaceValueAtIndex:inPropertyWithKey:withValue:")
    ObjCRuntime.msgSend(null, ptr, sel, index, key, value)
}

fun NSObject.insertValue_inPropertyWithKey(value: MemorySegment, key: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("insertValue:inPropertyWithKey:")
    ObjCRuntime.msgSend(null, ptr, sel, value, key)
}

fun NSObject.coerceValue_forKey(value: MemorySegment, key: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("coerceValue:forKey:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, value, key) as MemorySegment
}

// ── Category: NSScriptObjectSpecifiers on NSObject ─────────────────────────────────────────

/** @return NSArray<NSNumber *> * */
fun NSObject.indicesOfObjectsByEvaluatingObjectSpecifier(specifier: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("indicesOfObjectsByEvaluatingObjectSpecifier:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, specifier) as MemorySegment
}

fun NSObject.objectSpecifier(): MemorySegment {
    val sel = ObjCRuntime.sel("objectSpecifier")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property objectSpecifier
fun NSObject.objectSpecifier(): MemorySegment {
    val sel = ObjCRuntime.sel("objectSpecifier")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSComparisonMethods on NSObject ─────────────────────────────────────────

fun NSObject.isEqualTo(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isEqualTo:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.isLessThanOrEqualTo(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isLessThanOrEqualTo:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.isLessThan(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isLessThan:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.isGreaterThanOrEqualTo(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isGreaterThanOrEqualTo:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.isGreaterThan(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isGreaterThan:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.isNotEqualTo(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isNotEqualTo:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.doesContain(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("doesContain:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.isLike(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isLike:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.isCaseInsensitiveLike(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("isCaseInsensitiveLike:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

// ── Category: NSScriptingComparisonMethods on NSObject ─────────────────────────────────────────

fun NSObject.scriptingIsEqualTo(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scriptingIsEqualTo:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.scriptingIsLessThanOrEqualTo(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scriptingIsLessThanOrEqualTo:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.scriptingIsLessThan(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scriptingIsLessThan:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.scriptingIsGreaterThanOrEqualTo(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scriptingIsGreaterThanOrEqualTo:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.scriptingIsGreaterThan(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scriptingIsGreaterThan:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.scriptingBeginsWith(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scriptingBeginsWith:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.scriptingEndsWith(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scriptingEndsWith:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

fun NSObject.scriptingContains(`object`: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("scriptingContains:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, `object`) as BOOL
}

// ── Category: NSAccessibility on NSObject ─────────────────────────────────────────

/** @return NSArray<NSAccessibilityAttributeName> * */
fun NSObject.accessibilityAttributeNames(): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityAttributeNames")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObject.accessibilityAttributeValue(attribute: NSAccessibilityAttributeName): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityAttributeValue:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attribute) as MemorySegment
}

fun NSObject.accessibilityIsAttributeSettable(attribute: NSAccessibilityAttributeName): BOOL {
    val sel = ObjCRuntime.sel("accessibilityIsAttributeSettable:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, attribute) as BOOL
}

fun NSObject.accessibilitySetValue_forAttribute(value: MemorySegment, attribute: NSAccessibilityAttributeName): Unit {
    val sel = ObjCRuntime.sel("accessibilitySetValue:forAttribute:")
    ObjCRuntime.msgSend(null, ptr, sel, value, attribute)
}

/** @return NSArray<NSAccessibilityParameterizedAttributeName> * */
fun NSObject.accessibilityParameterizedAttributeNames(): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityParameterizedAttributeNames")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObject.accessibilityAttributeValue_forParameter(attribute: NSAccessibilityParameterizedAttributeName, parameter: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityAttributeValue:forParameter:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attribute, parameter) as MemorySegment
}

/** @return NSArray<NSAccessibilityActionName> * */
fun NSObject.accessibilityActionNames(): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityActionNames")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObject.accessibilityActionDescription(action: NSAccessibilityActionName): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityActionDescription:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, action) as MemorySegment
}

fun NSObject.accessibilityPerformAction(action: NSAccessibilityActionName): Unit {
    val sel = ObjCRuntime.sel("accessibilityPerformAction:")
    ObjCRuntime.msgSend(null, ptr, sel, action)
}

fun NSObject.accessibilityIsIgnored(): BOOL {
    val sel = ObjCRuntime.sel("accessibilityIsIgnored")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSObject.accessibilityHitTest(point: NSPoint): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityHitTest:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, point) as MemorySegment
}

fun NSObject.accessibilityIndexOfChild(child: MemorySegment): NSUInteger {
    val sel = ObjCRuntime.sel("accessibilityIndexOfChild:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, child) as NSUInteger
}

fun NSObject.accessibilityArrayAttributeCount(attribute: NSAccessibilityAttributeName): NSUInteger {
    val sel = ObjCRuntime.sel("accessibilityArrayAttributeCount:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, attribute) as NSUInteger
}

fun NSObject.accessibilityArrayAttributeValues_index_maxCount(attribute: NSAccessibilityAttributeName, index: NSUInteger, maxCount: NSUInteger): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityArrayAttributeValues:index:maxCount:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, attribute, index, maxCount) as MemorySegment
}

fun NSObject.accessibilityFocusedUIElement(): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityFocusedUIElement")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSObject.accessibilityNotifiesWhenDestroyed(): BOOL {
    val sel = ObjCRuntime.sel("accessibilityNotifiesWhenDestroyed")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property accessibilityFocusedUIElement
fun NSObject.accessibilityFocusedUIElement(): MemorySegment {
    val sel = ObjCRuntime.sel("accessibilityFocusedUIElement")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property accessibilityNotifiesWhenDestroyed
fun NSObject.accessibilityNotifiesWhenDestroyed(): BOOL {
    val sel = ObjCRuntime.sel("accessibilityNotifiesWhenDestroyed")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSAccessibilityAdditions on NSObject ─────────────────────────────────────────

fun NSObject.accessibilitySetOverrideValue_forAttribute(value: MemorySegment, attribute: NSAccessibilityAttributeName): BOOL {
    val sel = ObjCRuntime.sel("accessibilitySetOverrideValue:forAttribute:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, value, attribute) as BOOL
}

// ── Category: NSPasteboardOwner on NSObject ─────────────────────────────────────────

fun NSObject.pasteboard_provideDataForType(sender: MemorySegment, type: NSPasteboardType): Unit {
    val sel = ObjCRuntime.sel("pasteboard:provideDataForType:")
    ObjCRuntime.msgSend(null, ptr, sel, sender, type)
}

fun NSObject.pasteboardChangedOwner(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("pasteboardChangedOwner:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSDraggingSourceDeprecated on NSObject ─────────────────────────────────────────

/** @return NSArray<NSString *> * */
fun NSObject.namesOfPromisedFilesDroppedAtDestination(dropDestination: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("namesOfPromisedFilesDroppedAtDestination:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, dropDestination) as MemorySegment
}

fun NSObject.draggingSourceOperationMaskForLocal(flag: BOOL): NSDragOperation {
    val sel = ObjCRuntime.sel("draggingSourceOperationMaskForLocal:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, flag) as NSDragOperation
}

fun NSObject.draggedImage_beganAt(image: MemorySegment, screenPoint: NSPoint): Unit {
    val sel = ObjCRuntime.sel("draggedImage:beganAt:")
    ObjCRuntime.msgSend(null, ptr, sel, image, screenPoint)
}

fun NSObject.draggedImage_endedAt_operation(image: MemorySegment, screenPoint: NSPoint, operation: NSDragOperation): Unit {
    val sel = ObjCRuntime.sel("draggedImage:endedAt:operation:")
    ObjCRuntime.msgSend(null, ptr, sel, image, screenPoint, operation)
}

fun NSObject.draggedImage_movedTo(image: MemorySegment, screenPoint: NSPoint): Unit {
    val sel = ObjCRuntime.sel("draggedImage:movedTo:")
    ObjCRuntime.msgSend(null, ptr, sel, image, screenPoint)
}

fun NSObject.ignoreModifierKeysWhileDragging(): BOOL {
    val sel = ObjCRuntime.sel("ignoreModifierKeysWhileDragging")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSObject.draggedImage_endedAt_deposited(image: MemorySegment, screenPoint: NSPoint, flag: BOOL): Unit {
    val sel = ObjCRuntime.sel("draggedImage:endedAt:deposited:")
    ObjCRuntime.msgSend(null, ptr, sel, image, screenPoint, flag)
}

// ── Category: NSLayerDelegateContentsScaleUpdating on NSObject ─────────────────────────────────────────

fun NSObject.layer_shouldInheritContentsScale_fromWindow(layer: MemorySegment, newScale: CGFloat, window: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("layer:shouldInheritContentsScale:fromWindow:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, layer, newScale, window) as BOOL
}

// ── Category: NSToolTipOwner on NSObject ─────────────────────────────────────────

fun NSObject.view_stringForToolTip_point_userData(view: MemorySegment, tag: NSToolTipTag, point: NSPoint, `data`: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("view:stringForToolTip:point:userData:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, view, tag, point, `data`) as MemorySegment
}

// ── Category: NSMenuValidation on NSObject ─────────────────────────────────────────

fun NSObject.validateMenuItem(menuItem: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("validateMenuItem:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, menuItem) as BOOL
}

// ── Category: NSKeyValueBindingCreation on NSObject ─────────────────────────────────────────

fun NSObject.valueClassForBinding(binding: NSBindingName): Class {
    val sel = ObjCRuntime.sel("valueClassForBinding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, binding) as Class
}

fun NSObject.bind_toObject_withKeyPath_options(binding: NSBindingName, observable: MemorySegment, keyPath: MemorySegment, options: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("bind:toObject:withKeyPath:options:")
    ObjCRuntime.msgSend(null, ptr, sel, binding, observable, keyPath, options)
}

fun NSObject.unbind(binding: NSBindingName): Unit {
    val sel = ObjCRuntime.sel("unbind:")
    ObjCRuntime.msgSend(null, ptr, sel, binding)
}

/** @return NSDictionary<NSBindingInfoKey,id> * */
fun NSObject.infoForBinding(binding: NSBindingName): MemorySegment {
    val sel = ObjCRuntime.sel("infoForBinding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, binding) as MemorySegment
}

/** @return NSArray<NSAttributeDescription *> * */
fun NSObject.optionDescriptionsForBinding(binding: NSBindingName): MemorySegment {
    val sel = ObjCRuntime.sel("optionDescriptionsForBinding:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, binding) as MemorySegment
}

/** @return NSArray<NSBindingName> * */
fun NSObject.exposedBindings(): MemorySegment {
    val sel = ObjCRuntime.sel("exposedBindings")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class method: +[NSObject exposeBinding:]
fun NSObject_exposeBinding(binding: NSBindingName): Unit {
    val sel = ObjCRuntime.sel("exposeBinding:")
    val cls = ObjCRuntime.getClass("NSObject")
    ObjCRuntime.msgSend(null, cls, sel, binding)
}

// @property exposedBindings
/** @return NSArray<NSBindingName> * */
fun NSObject.exposedBindings(): MemorySegment {
    val sel = ObjCRuntime.sel("exposedBindings")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSPlaceholders on NSObject ─────────────────────────────────────────

// Class method: +[NSObject setDefaultPlaceholder:forMarker:withBinding:]
fun NSObject_setDefaultPlaceholder_forMarker_withBinding(placeholder: MemorySegment, marker: MemorySegment, binding: NSBindingName): Unit {
    val sel = ObjCRuntime.sel("setDefaultPlaceholder:forMarker:withBinding:")
    val cls = ObjCRuntime.getClass("NSObject")
    ObjCRuntime.msgSend(null, cls, sel, placeholder, marker, binding)
}

// Class method: +[NSObject defaultPlaceholderForMarker:withBinding:]
fun NSObject_defaultPlaceholderForMarker_withBinding(marker: MemorySegment, binding: NSBindingName): MemorySegment {
    val sel = ObjCRuntime.sel("defaultPlaceholderForMarker:withBinding:")
    val cls = ObjCRuntime.getClass("NSObject")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, marker, binding) as MemorySegment
}

// ── Category: NSEditor on NSObject ─────────────────────────────────────────

fun NSObject.discardEditing(): Unit {
    val sel = ObjCRuntime.sel("discardEditing")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSObject.commitEditing(): BOOL {
    val sel = ObjCRuntime.sel("commitEditing")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSObject.commitEditingWithDelegate_didCommitSelector_contextInfo(delegate: MemorySegment, didCommitSelector: MemorySegment, contextInfo: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("commitEditingWithDelegate:didCommitSelector:contextInfo:")
    ObjCRuntime.msgSend(null, ptr, sel, delegate, didCommitSelector, contextInfo)
}

fun NSObject.commitEditingAndReturnError(error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("commitEditingAndReturnError:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
}

// ── Category: NSEditorRegistration on NSObject ─────────────────────────────────────────

fun NSObject.objectDidBeginEditing(editor: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("objectDidBeginEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, editor)
}

fun NSObject.objectDidEndEditing(editor: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("objectDidEndEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, editor)
}

// ── Category: NSControlSubclassNotifications on NSObject ─────────────────────────────────────────

fun NSObject.controlTextDidBeginEditing(obj: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("controlTextDidBeginEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, obj)
}

fun NSObject.controlTextDidEndEditing(obj: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("controlTextDidEndEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, obj)
}

fun NSObject.controlTextDidChange(obj: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("controlTextDidChange:")
    ObjCRuntime.msgSend(null, ptr, sel, obj)
}

// ── Category: NSFontManagerDelegate on NSObject ─────────────────────────────────────────

fun NSObject.fontManager_willIncludeFont(sender: MemorySegment, fontName: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("fontManager:willIncludeFont:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, sender, fontName) as BOOL
}

// ── Category: NSFontManagerResponderMethod on NSObject ─────────────────────────────────────────

fun NSObject.changeFont(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("changeFont:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSFontPanelValidationAdditions on NSObject ─────────────────────────────────────────

fun NSObject.validModesForFontPanel(fontPanel: MemorySegment): NSFontPanelModeMask {
    val sel = ObjCRuntime.sel("validModesForFontPanel:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, fontPanel) as NSFontPanelModeMask
}

// ── Category: NSColorPanelResponderMethod on NSObject ─────────────────────────────────────────

fun NSObject.changeColor(sender: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("changeColor:")
    ObjCRuntime.msgSend(null, ptr, sel, sender)
}

// ── Category: NSNibAwaking on NSObject ─────────────────────────────────────────

fun NSObject.awakeFromNib(): Unit {
    val sel = ObjCRuntime.sel("awakeFromNib")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSObject.prepareForInterfaceBuilder(): Unit {
    val sel = ObjCRuntime.sel("prepareForInterfaceBuilder")
    ObjCRuntime.msgSend(null, ptr, sel)
}

// ── Category: NSSavePanelDelegateDeprecated on NSObject ─────────────────────────────────────────

fun NSObject.panel_isValidFilename(sender: MemorySegment, filename: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("panel:isValidFilename:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, sender, filename) as BOOL
}

fun NSObject.panel_directoryDidChange(sender: MemorySegment, path: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("panel:directoryDidChange:")
    ObjCRuntime.msgSend(null, ptr, sel, sender, path)
}

fun NSObject.panel_compareFilename_with_caseSensitive(sender: MemorySegment, name1: MemorySegment, name2: MemorySegment, caseSensitive: BOOL): NSComparisonResult {
    val sel = ObjCRuntime.sel("panel:compareFilename:with:caseSensitive:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sender, name1, name2, caseSensitive) as NSComparisonResult
}

fun NSObject.panel_shouldShowFilename(sender: MemorySegment, filename: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("panel:shouldShowFilename:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, sender, filename) as BOOL
}

// ── Category: NSDeprecatedTextStorageDelegateInterface on NSObject ─────────────────────────────────────────

fun NSObject.textStorageWillProcessEditing(notification: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("textStorageWillProcessEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, notification)
}

fun NSObject.textStorageDidProcessEditing(notification: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("textStorageDidProcessEditing:")
    ObjCRuntime.msgSend(null, ptr, sel, notification)
}

// ── Category: NSToolbarItemValidation on NSObject ─────────────────────────────────────────

fun NSObject.validateToolbarItem(item: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("validateToolbarItem:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, item) as BOOL
}

// ── Category: NSTableViewDataSourceDeprecated on NSObject ─────────────────────────────────────────

fun NSObject.tableView_writeRows_toPasteboard(tableView: MemorySegment, rows: MemorySegment, pboard: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("tableView:writeRows:toPasteboard:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, tableView, rows, pboard) as BOOL
}

// ── Category: NSApplicationScriptingDelegation on NSObject ─────────────────────────────────────────

fun NSObject.application_delegateHandlesKey(sender: MemorySegment, key: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("application:delegateHandlesKey:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, sender, key) as BOOL
}


/**
 * Kotlin/JVM wrapper for Objective-C class: NSProcessInfo
 * Superclass: NSObject
 */
open class NSProcessInfo(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSProcessInfo") }
        
        fun processInfo(): MemorySegment {
            val sel = ObjCRuntime.sel("processInfo")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun operatingSystem(): NSUInteger {
        val sel = ObjCRuntime.sel("operatingSystem")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    fun operatingSystemName(): MemorySegment {
        val sel = ObjCRuntime.sel("operatingSystemName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun operatingSystemNameAsString(): String = ObjCRuntime.toJavaString(operatingSystemName())
    
    fun isOperatingSystemAtLeastVersion(version: NSOperatingSystemVersion): BOOL {
        val sel = ObjCRuntime.sel("isOperatingSystemAtLeastVersion:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, ObjCRuntime.ObjCStructArg(version, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("majorVersion"), ValueLayout.JAVA_LONG.withName("minorVersion"), ValueLayout.JAVA_LONG.withName("patchVersion")).withName("NSOperatingSystemVersion"))) as BOOL
    }
    
    fun disableSuddenTermination(): Unit {
        val sel = ObjCRuntime.sel("disableSuddenTermination")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun enableSuddenTermination(): Unit {
        val sel = ObjCRuntime.sel("enableSuddenTermination")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun disableAutomaticTermination(reason: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("disableAutomaticTermination:")
        ObjCRuntime.msgSend(null, ptr, sel, reason)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun disableAutomaticTermination(reason: String): Unit = disableAutomaticTermination(ObjCRuntime.newNSString(Arena.global(), reason))
    
    fun enableAutomaticTermination(reason: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("enableAutomaticTermination:")
        ObjCRuntime.msgSend(null, ptr, sel, reason)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun enableAutomaticTermination(reason: String): Unit = enableAutomaticTermination(ObjCRuntime.newNSString(Arena.global(), reason))
    
    // @property processInfo
    fun processInfo(): MemorySegment {
        val sel = ObjCRuntime.sel("processInfo")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property environment
    /** @return NSDictionary<NSString *,NSString *> * */
    fun environment(): MemorySegment {
        val sel = ObjCRuntime.sel("environment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property arguments
    /** @return NSArray<NSString *> * */
    fun arguments(): MemorySegment {
        val sel = ObjCRuntime.sel("arguments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property hostName
    fun hostName(): MemorySegment {
        val sel = ObjCRuntime.sel("hostName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun hostNameAsString(): String = ObjCRuntime.toJavaString(hostName())
    
    // @property processName
    fun processName(): MemorySegment {
        val sel = ObjCRuntime.sel("processName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setProcessName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setProcessName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun processNameAsString(): String = ObjCRuntime.toJavaString(processName())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setProcessName(value: String) = setProcessName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property processIdentifier
    fun processIdentifier(): Int {
        val sel = ObjCRuntime.sel("processIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    
    // @property globallyUniqueString
    fun globallyUniqueString(): MemorySegment {
        val sel = ObjCRuntime.sel("globallyUniqueString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun globallyUniqueStringAsString(): String = ObjCRuntime.toJavaString(globallyUniqueString())
    
    // @property operatingSystemVersionString
    fun operatingSystemVersionString(): MemorySegment {
        val sel = ObjCRuntime.sel("operatingSystemVersionString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun operatingSystemVersionStringAsString(): String = ObjCRuntime.toJavaString(operatingSystemVersionString())
    
    // @property operatingSystemVersion
    fun operatingSystemVersion(): NSOperatingSystemVersion {
        val sel = ObjCRuntime.sel("operatingSystemVersion")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("majorVersion"), ValueLayout.JAVA_LONG.withName("minorVersion"), ValueLayout.JAVA_LONG.withName("patchVersion")).withName("NSOperatingSystemVersion"), ptr, sel) as NSOperatingSystemVersion
    }
    
    // @property processorCount
    fun processorCount(): NSUInteger {
        val sel = ObjCRuntime.sel("processorCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property activeProcessorCount
    fun activeProcessorCount(): NSUInteger {
        val sel = ObjCRuntime.sel("activeProcessorCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property physicalMemory
    fun physicalMemory(): Any {
        val sel = ObjCRuntime.sel("physicalMemory")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as Any
    }
    
    // @property systemUptime
    fun systemUptime(): NSTimeInterval {
        val sel = ObjCRuntime.sel("systemUptime")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
    // @property automaticTerminationSupportEnabled
    fun automaticTerminationSupportEnabled(): BOOL {
        val sel = ObjCRuntime.sel("automaticTerminationSupportEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticTerminationSupportEnabled(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticTerminationSupportEnabled:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: environment: MemorySegment
    // ivar: arguments: MemorySegment
    // ivar: hostName: MemorySegment
    // ivar: name: MemorySegment
    // ivar: automaticTerminationOptOutCounter: NSInteger
}

// ── Category: NSProcessInfoActivity on NSProcessInfo ─────────────────────────────────────────

/** @return id<NSObject> */
fun NSProcessInfo.beginActivityWithOptions_reason(options: NSActivityOptions, reason: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("beginActivityWithOptions:reason:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options, reason) as MemorySegment
}

fun NSProcessInfo.endActivity(activity: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("endActivity:")
    ObjCRuntime.msgSend(null, ptr, sel, activity)
}

fun NSProcessInfo.performActivityWithOptions_reason_usingBlock(options: NSActivityOptions, reason: MemorySegment, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performActivityWithOptions:reason:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, options, reason, block)
}

fun NSProcessInfo.performExpiringActivityWithReason_usingBlock(reason: MemorySegment, block: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("performExpiringActivityWithReason:usingBlock:")
    ObjCRuntime.msgSend(null, ptr, sel, reason, block)
}

// ── Category: NSUserInformation on NSProcessInfo ─────────────────────────────────────────

fun NSProcessInfo.userName(): MemorySegment {
    val sel = ObjCRuntime.sel("userName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSProcessInfo.fullUserName(): MemorySegment {
    val sel = ObjCRuntime.sel("fullUserName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property userName
fun NSProcessInfo.userName(): MemorySegment {
    val sel = ObjCRuntime.sel("userName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property fullUserName
fun NSProcessInfo.fullUserName(): MemorySegment {
    val sel = ObjCRuntime.sel("fullUserName")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSProcessInfoThermalState on NSProcessInfo ─────────────────────────────────────────

fun NSProcessInfo.thermalState(): NSProcessInfoThermalState {
    val sel = ObjCRuntime.sel("thermalState")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSProcessInfoThermalState
}

// @property thermalState
fun NSProcessInfo.thermalState(): NSProcessInfoThermalState {
    val sel = ObjCRuntime.sel("thermalState")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSProcessInfoThermalState
}

// ── Category: NSProcessInfoPowerState on NSProcessInfo ─────────────────────────────────────────

fun NSProcessInfo.isLowPowerModeEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isLowPowerModeEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property lowPowerModeEnabled
fun NSProcessInfo.isLowPowerModeEnabled(): BOOL {
    val sel = ObjCRuntime.sel("isLowPowerModeEnabled")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSProcessInfoPlatform on NSProcessInfo ─────────────────────────────────────────

fun NSProcessInfo.isMacCatalystApp(): BOOL {
    val sel = ObjCRuntime.sel("isMacCatalystApp")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSProcessInfo.isiOSAppOnMac(): BOOL {
    val sel = ObjCRuntime.sel("isiOSAppOnMac")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

fun NSProcessInfo.isiOSAppOnVision(): BOOL {
    val sel = ObjCRuntime.sel("isiOSAppOnVision")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property macCatalystApp
fun NSProcessInfo.isMacCatalystApp(): BOOL {
    val sel = ObjCRuntime.sel("isMacCatalystApp")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property iOSAppOnMac
fun NSProcessInfo.isiOSAppOnMac(): BOOL {
    val sel = ObjCRuntime.sel("isiOSAppOnMac")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property iOSAppOnVision
fun NSProcessInfo.isiOSAppOnVision(): BOOL {
    val sel = ObjCRuntime.sel("isiOSAppOnVision")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}


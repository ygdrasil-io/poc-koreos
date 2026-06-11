/**
 * Kotlin/JVM wrapper for Objective-C class: NSTask
 * Superclass: NSObject
 */
open class NSTask(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSTask") }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun launchAndReturnError(error: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("launchAndReturnError:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, error) as BOOL
    }
    
    fun interrupt(): Unit {
        val sel = ObjCRuntime.sel("interrupt")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun terminate(): Unit {
        val sel = ObjCRuntime.sel("terminate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun suspend(): BOOL {
        val sel = ObjCRuntime.sel("suspend")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun resume(): BOOL {
        val sel = ObjCRuntime.sel("resume")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property executableURL
    fun executableURL(): MemorySegment {
        val sel = ObjCRuntime.sel("executableURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setExecutableURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setExecutableURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property arguments
    /** @return NSArray<NSString *> * */
    fun arguments(): MemorySegment {
        val sel = ObjCRuntime.sel("arguments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setArguments(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setArguments:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property environment
    /** @return NSDictionary<NSString *,NSString *> * */
    fun environment(): MemorySegment {
        val sel = ObjCRuntime.sel("environment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setEnvironment(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEnvironment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentDirectoryURL
    fun currentDirectoryURL(): MemorySegment {
        val sel = ObjCRuntime.sel("currentDirectoryURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setCurrentDirectoryURL(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setCurrentDirectoryURL:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property launchRequirementData
    fun launchRequirementData(): MemorySegment {
        val sel = ObjCRuntime.sel("launchRequirementData")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setLaunchRequirementData(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setLaunchRequirementData:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standardInput
    fun standardInput(): MemorySegment {
        val sel = ObjCRuntime.sel("standardInput")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandardInput(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandardInput:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standardOutput
    fun standardOutput(): MemorySegment {
        val sel = ObjCRuntime.sel("standardOutput")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandardOutput(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandardOutput:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property standardError
    fun standardError(): MemorySegment {
        val sel = ObjCRuntime.sel("standardError")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setStandardError(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStandardError:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property processIdentifier
    fun processIdentifier(): Int {
        val sel = ObjCRuntime.sel("processIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    
    // @property running
    fun isRunning(): BOOL {
        val sel = ObjCRuntime.sel("isRunning")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property terminationStatus
    fun terminationStatus(): Int {
        val sel = ObjCRuntime.sel("terminationStatus")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as Int
    }
    
    // @property terminationReason
    fun terminationReason(): NSTaskTerminationReason {
        val sel = ObjCRuntime.sel("terminationReason")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSTaskTerminationReason
    }
    
    // @property terminationHandler
    fun terminationHandler(): MemorySegment {
        val sel = ObjCRuntime.sel("terminationHandler")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setTerminationHandler(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setTerminationHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property qualityOfService
    fun qualityOfService(): NSQualityOfService {
        val sel = ObjCRuntime.sel("qualityOfService")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSQualityOfService
    }
    fun setQualityOfService(value: NSQualityOfService) {
        val sel = ObjCRuntime.sel("setQualityOfService:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSTaskConveniences on NSTask ─────────────────────────────────────────

fun NSTask.waitUntilExit(): Unit {
    val sel = ObjCRuntime.sel("waitUntilExit")
    ObjCRuntime.msgSend(null, ptr, sel)
}

// Class method: +[NSTask launchedTaskWithExecutableURL:arguments:error:terminationHandler:]
fun NSTask_launchedTaskWithExecutableURL_arguments_error_terminationHandler(url: MemorySegment, arguments: MemorySegment, error: MemorySegment, terminationHandler: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("launchedTaskWithExecutableURL:arguments:error:terminationHandler:")
    val cls = ObjCRuntime.getClass("NSTask")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, url, arguments, error, terminationHandler) as MemorySegment
}

// ── Category: NSDeprecated on NSTask ─────────────────────────────────────────

fun NSTask.launch(): Unit {
    val sel = ObjCRuntime.sel("launch")
    ObjCRuntime.msgSend(null, ptr, sel)
}

fun NSTask.launchPath(): MemorySegment {
    val sel = ObjCRuntime.sel("launchPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTask.setLaunchPath(launchPath: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setLaunchPath:")
    ObjCRuntime.msgSend(null, ptr, sel, launchPath)
}

fun NSTask.currentDirectoryPath(): MemorySegment {
    val sel = ObjCRuntime.sel("currentDirectoryPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSTask.setCurrentDirectoryPath(currentDirectoryPath: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("setCurrentDirectoryPath:")
    ObjCRuntime.msgSend(null, ptr, sel, currentDirectoryPath)
}

// Class method: +[NSTask launchedTaskWithLaunchPath:arguments:]
fun NSTask_launchedTaskWithLaunchPath_arguments(path: MemorySegment, arguments: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("launchedTaskWithLaunchPath:arguments:")
    val cls = ObjCRuntime.getClass("NSTask")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, path, arguments) as MemorySegment
}

// @property launchPath
fun NSTask.launchPath(): MemorySegment {
    val sel = ObjCRuntime.sel("launchPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSTask.setLaunchPath(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setLaunchPath:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}

// @property currentDirectoryPath
fun NSTask.currentDirectoryPath(): MemorySegment {
    val sel = ObjCRuntime.sel("currentDirectoryPath")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}
fun NSTask.setCurrentDirectoryPath(value: MemorySegment) {
    val sel = ObjCRuntime.sel("setCurrentDirectoryPath:")
    ObjCRuntime.msgSend(null, ptr, sel, value)
}


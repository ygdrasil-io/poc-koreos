package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSRunningApplication
 * Superclass: NSObject
 */
open class NSRunningApplication(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSRunningApplication") }
        
        /** @return NSArray<NSRunningApplication *> * */
        open fun runningApplicationsWithBundleIdentifier(bundleIdentifier: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("runningApplicationsWithBundleIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, bundleIdentifier) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun runningApplicationsWithBundleIdentifier(bundleIdentifier: String): MemorySegment = runningApplicationsWithBundleIdentifier(ObjCRuntime.newNSString(Arena.global(), bundleIdentifier))
        
        open fun runningApplicationWithProcessIdentifier(pid: pid_t): MemorySegment {
            val sel = ObjCRuntime.sel("runningApplicationWithProcessIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, pid) as MemorySegment
        }
        
        open fun terminateAutomaticallyTerminableApplications(): Unit {
            val sel = ObjCRuntime.sel("terminateAutomaticallyTerminableApplications")
            ObjCRuntime.msgSend(null, _class, sel)
        }
        
        open fun currentApplication(): MemorySegment {
            val sel = ObjCRuntime.sel("currentApplication")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun hide(): BOOL {
        val sel = ObjCRuntime.sel("hide")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun unhide(): BOOL {
        val sel = ObjCRuntime.sel("unhide")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun activateFromApplication_options(application: MemorySegment, options: NSApplicationActivationOptions): BOOL {
        val sel = ObjCRuntime.sel("activateFromApplication:options:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, application, options) as BOOL
    }
    
    open fun activateWithOptions(options: NSApplicationActivationOptions): BOOL {
        val sel = ObjCRuntime.sel("activateWithOptions:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, options) as BOOL
    }
    
    open fun terminate(): BOOL {
        val sel = ObjCRuntime.sel("terminate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun forceTerminate(): BOOL {
        val sel = ObjCRuntime.sel("forceTerminate")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property terminated
    open fun isTerminated(): BOOL {
        val sel = ObjCRuntime.sel("isTerminated")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property finishedLaunching
    open fun isFinishedLaunching(): BOOL {
        val sel = ObjCRuntime.sel("isFinishedLaunching")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property hidden
    open fun isHidden(): BOOL {
        val sel = ObjCRuntime.sel("isHidden")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property active
    open fun isActive(): BOOL {
        val sel = ObjCRuntime.sel("isActive")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property ownsMenuBar
    open fun ownsMenuBar(): BOOL {
        val sel = ObjCRuntime.sel("ownsMenuBar")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property activationPolicy
    open fun activationPolicy(): NSApplicationActivationPolicy {
        val sel = ObjCRuntime.sel("activationPolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSApplicationActivationPolicy
    }
    
    // @property localizedName
    open fun localizedName(): MemorySegment {
        val sel = ObjCRuntime.sel("localizedName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localizedNameAsString(): String = ObjCRuntime.toJavaString(localizedName())
    
    // @property bundleIdentifier
    open fun bundleIdentifier(): MemorySegment {
        val sel = ObjCRuntime.sel("bundleIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun bundleIdentifierAsString(): String = ObjCRuntime.toJavaString(bundleIdentifier())
    
    // @property bundleURL
    open fun bundleURL(): MemorySegment {
        val sel = ObjCRuntime.sel("bundleURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property executableURL
    open fun executableURL(): MemorySegment {
        val sel = ObjCRuntime.sel("executableURL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property processIdentifier
    open fun processIdentifier(): pid_t {
        val sel = ObjCRuntime.sel("processIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_INT, ptr, sel) as pid_t
    }
    
    // @property launchDate
    open fun launchDate(): MemorySegment {
        val sel = ObjCRuntime.sel("launchDate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property icon
    open fun icon(): MemorySegment {
        val sel = ObjCRuntime.sel("icon")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property executableArchitecture
    open fun executableArchitecture(): NSInteger {
        val sel = ObjCRuntime.sel("executableArchitecture")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property currentApplication
    open fun currentApplication(): MemorySegment {
        val sel = ObjCRuntime.sel("currentApplication")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}


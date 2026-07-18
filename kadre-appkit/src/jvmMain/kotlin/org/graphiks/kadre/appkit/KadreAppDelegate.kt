/**
 * Kotlin implementation of `NSApplicationDelegate` via ObjC runtime subclassing.
 *
 * Uses Panama FFM upcall stubs to expose Kotlin static functions
 * (`@JvmStatic`) as Objective-C method implementations.
 *
 * Dispatch strategy: the ObjC class `KadreAppDelegateNative` does not embed
 * a pointer to the Kotlin delegate. Instead, instances are indexed in a
 * global [java.util.concurrent.ConcurrentHashMap] keyed by the memory
 * address (`MemorySegment.address()`) of the ObjC `self`. Since the first
 * argument of any ObjC upcall is `self`, this is enough to retrieve the
 * target Kotlin instance during a native callback.
 */
package org.graphiks.kadre.appkit

import org.graphiks.kffi.objc.NSApplicationTerminateReply
import org.graphiks.kffi.objc.ObjCRuntime
import org.graphiks.kffi.objc.ObjCSubclassing
import org.graphiks.kadre.core.ActiveEventLoop
import org.graphiks.kadre.core.ApplicationHandler
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal interface AppKitApplicationDelegateCallbacks {
    fun onDidFinishLaunching() = Unit
    fun onDidBecomeActive() = Unit
    fun onWillResignActive() = Unit
    fun onWillTerminate() = Unit
    fun onShouldTerminate(): Long = NSApplicationTerminateReply.NSTerminateNow.value
    fun captureCallbackFailure(context: String, failure: Throwable) = Unit
}

class KadreAppDelegate(
    private val handler: ApplicationHandler,
    private val eventLoop: ActiveEventLoop,
) {
    private val released = AtomicBoolean(false)
    private val routeCallbacks: AppKitApplicationDelegateCallbacks
    private val routeToken: AppKitNativeCallbackToken
    /** Pointer to the Objective-C object wrapped by this delegate. */
    val ptr: MemorySegment

    init {
        ensureClassRegistered()

        val cls = ObjCRuntime.getClass("KadreAppDelegateNative")
        val allocated = ObjCRuntime.msgSend(
            ValueLayout.ADDRESS,
            cls,
            ObjCRuntime.sel("alloc"),
        ) as MemorySegment
        ptr = ObjCRuntime.msgSend(
            ValueLayout.ADDRESS,
            allocated,
            ObjCRuntime.sel("init"),
        ) as MemorySegment

        routeCallbacks = object : AppKitApplicationDelegateCallbacks {
            override fun onDidFinishLaunching() = this@KadreAppDelegate.onDidFinishLaunching()
            override fun onDidBecomeActive() = this@KadreAppDelegate.onDidBecomeActive()
            override fun onWillResignActive() = this@KadreAppDelegate.onWillResignActive()
            override fun onWillTerminate() = this@KadreAppDelegate.onWillTerminate()
            override fun onShouldTerminate(): Long = this@KadreAppDelegate.onShouldTerminate()
            override fun captureCallbackFailure(context: String, failure: Throwable) {
                (eventLoop as? AppKitEventLoop)?.recordCallbackFailure(context, failure)
            }
        }
        routeToken = registerDelegateRoute(ptr, routeCallbacks)
    }

    /** Kotlin callback for `applicationDidFinishLaunching:`. */
    fun onDidFinishLaunching() {
        (eventLoop as AppKitEventLoop).didLaunch()
    }

    internal fun onDidBecomeActive() {
        (eventLoop as AppKitEventLoop).didBecomeActive()
    }

    internal fun onWillResignActive() {
        (eventLoop as AppKitEventLoop).willResignActive()
    }

    internal fun onWillTerminate() {
        (eventLoop as AppKitEventLoop).noteApplicationWillTerminate()
    }

    /**
     * Kotlin callback for `applicationShouldTerminate:`.
     *
     * Returns `NSTerminateNow` when the loop is already shutting down,
     * `NSTerminateCancel` otherwise — shutdown is driven on the Kadre side via
     * [ActiveEventLoop.exit].
     */
    fun onShouldTerminate(): Long {
        return if (eventLoop.isExiting) {
            NSApplicationTerminateReply.NSTerminateNow.value
        } else {
            NSApplicationTerminateReply.NSTerminateCancel.value
        }
    }

    internal fun releaseNative() {
        if (!released.compareAndSet(false, true)) return
        unregisterDelegate(routeToken, routeCallbacks)
        ObjCRuntime.msgSend(null, ptr, ObjCRuntime.sel("release"))
    }

    companion object {
        /** Global table: native generation token → associated Kotlin delegate. */
        private val delegateTable = ConcurrentHashMap<AppKitNativeCallbackToken, AppKitApplicationDelegateCallbacks>()

        private fun registerDelegateRoute(
            receiver: MemorySegment,
            callbacks: AppKitApplicationDelegateCallbacks,
        ): AppKitNativeCallbackToken = AppKitNativeCallbackTokens.attach(receiver).also { token ->
            delegateTable[token] = callbacks
        }

        internal fun registerDelegateRoute(
            address: Long,
            callbacks: AppKitApplicationDelegateCallbacks,
        ): AppKitNativeCallbackToken = AppKitNativeCallbackTokens.attachTestAddress(address).also { token ->
            delegateTable[token] = callbacks
        }

        internal fun unregisterDelegate(address: Long) {
            val token = AppKitNativeCallbackTokens.readTestAddress(address) ?: return
            delegateTable.remove(token)
            AppKitNativeCallbackTokens.detachTestAddress(address, token)
        }

        internal fun unregisterDelegate(address: Long, callbacks: AppKitApplicationDelegateCallbacks) {
            val token = delegateTable.entries.firstOrNull { it.value === callbacks }?.key ?: return
            delegateTable.remove(token, callbacks)
            AppKitNativeCallbackTokens.detachTestAddress(address, token)
        }

        internal fun unregisterDelegate(
            token: AppKitNativeCallbackToken,
            callbacks: AppKitApplicationDelegateCallbacks,
        ) {
            delegateTable.remove(token, callbacks)
        }

        internal fun registeredDelegateCount(): Int = delegateTable.size

        @Volatile
        private var classRegistered: Boolean = false

        @Synchronized
        internal fun ensureClassRegistered() {
            if (classRegistered) return

            val arena = Arena.global()
            val linker: Linker = Linker.nativeLinker()
            val lookup = MethodHandles.lookup()

            val cls = ObjCSubclassing.allocateClass("NSObject", "KadreAppDelegateNative")
            ObjCSubclassing.addProtocol(cls, "NSApplicationDelegate")

            fun addNotificationMethod(callbackName: String, selector: String) {
                val handle = lookup.findStatic(
                    Callbacks::class.java,
                    callbackName,
                    MethodType.methodType(
                        Void.TYPE,
                        MemorySegment::class.java,
                        MemorySegment::class.java,
                        MemorySegment::class.java,
                    ),
                )
                val stub = linker.upcallStub(
                    handle,
                    FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    arena,
                )
                ObjCSubclassing.addMethod(cls, selector, stub, "v@:@")
            }

            // NSUInteger applicationShouldTerminate(id self, SEL _cmd, id sender)
            val shouldTerminateHandle = lookup.findStatic(
                Callbacks::class.java,
                "applicationShouldTerminate",
                MethodType.methodType(
                    java.lang.Long.TYPE,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            )
            val shouldTerminateStub = linker.upcallStub(
                shouldTerminateHandle,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                arena,
            )

            addNotificationMethod("applicationDidFinishLaunching", "applicationDidFinishLaunching:")
            addNotificationMethod("applicationDidBecomeActive", "applicationDidBecomeActive:")
            addNotificationMethod("applicationWillResignActive", "applicationWillResignActive:")
            addNotificationMethod("applicationWillTerminate", "applicationWillTerminate:")
            ObjCSubclassing.addMethod(
                cls,
                "applicationShouldTerminate:",
                shouldTerminateStub,
                "Q@:@",
            )

            ObjCSubclassing.registerClass(cls)
            classRegistered = true
        }
    }

    /**
     * `@JvmStatic` trampolines invoked by the Panama upcall stubs.
     *
     * The static methods are essential: `Linker.upcallStub` cannot bind
     * instance methods because the ObjC `self` is passed as the first
     * argument, not via the Java receiver.
     */
    object Callbacks {
        @JvmStatic
        fun applicationDidFinishLaunching(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "applicationDidFinishLaunching") { it.onDidFinishLaunching() }
        }

        @JvmStatic
        fun applicationDidBecomeActive(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "applicationDidBecomeActive") { it.onDidBecomeActive() }
        }

        @JvmStatic
        fun applicationWillResignActive(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "applicationWillResignActive") { it.onWillResignActive() }
        }

        @JvmStatic
        fun applicationWillTerminate(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") notification: MemorySegment,
        ) {
            invokeSafely(self, "applicationWillTerminate") { it.onWillTerminate() }
        }

        @JvmStatic
        fun applicationShouldTerminate(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") sender: MemorySegment,
        ): Long = AppKitNativeCallbackBoundary.invokeOrDefault(
            NSApplicationTerminateReply.NSTerminateNow.value,
        ) {
            var callbacks: AppKitApplicationDelegateCallbacks? = null
            try {
                callbacks = AppKitNativeCallbackTokens.read(self)?.let(delegateTable::get)
                    ?: return@invokeOrDefault NSApplicationTerminateReply.NSTerminateNow.value
                callbacks.onShouldTerminate()
            } catch (failure: Throwable) {
                try {
                    callbacks?.captureCallbackFailure("applicationShouldTerminate", failure)
                } catch (_: Throwable) {
                    // No Kotlin exception may cross an Objective-C upcall.
                }
                NSApplicationTerminateReply.NSTerminateNow.value
            }
        }

        private inline fun invokeSafely(
            self: MemorySegment,
            context: String,
            crossinline callback: (AppKitApplicationDelegateCallbacks) -> Unit,
        ) {
            AppKitNativeCallbackBoundary.invoke {
                try {
                    val callbacks = AppKitNativeCallbackTokens.read(self)?.let(delegateTable::get)
                        ?: return@invoke
                    invokeTokenSafely(callbacks, context, callback)
                } catch (_: Throwable) {
                    // A native token lookup failure has no safe Kotlin route to capture it on.
                }
            }
        }

        private inline fun invokeTokenSafely(
            callbacks: AppKitApplicationDelegateCallbacks,
            context: String,
            callback: (AppKitApplicationDelegateCallbacks) -> Unit,
        ) {
            try {
                callback(callbacks)
            } catch (failure: Throwable) {
                try {
                    callbacks.captureCallbackFailure(context, failure)
                } catch (_: Throwable) {
                    // No Kotlin exception may cross an Objective-C upcall.
                }
            }
        }

        internal fun applicationDidBecomeActiveForToken(token: AppKitNativeCallbackToken) {
            AppKitNativeCallbackBoundary.invoke {
                val callbacks = delegateTable[token] ?: return@invoke
                invokeTokenSafely(callbacks, "applicationDidBecomeActive") { it.onDidBecomeActive() }
            }
        }
    }
}

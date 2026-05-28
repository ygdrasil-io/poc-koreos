/**
 * CFRunLoopObserver pour le dispatch de WindowEvent.RedrawRequested (GRA-134)
 * et l'appel à ApplicationHandler.aboutToWait (GRA-135).
 *
 * Installé sur kCFRunLoopBeforeWaiting : pour chaque AppKitWindow ayant
 * needsRedraw=true, dispatch WindowEvent.RedrawRequested et reset le flag.
 * Puis appelle aboutToWait(eventLoop) — après tous les RedrawRequested.
 * Coalescing natif : plusieurs requestRedraw() → un seul event par itération.
 */
package io.ygdrasil.koreos.appkit

import io.ygdrasil.koreos.core.ActiveEventLoop
import io.ygdrasil.koreos.core.ApplicationHandler
import io.ygdrasil.koreos.core.WindowEvent
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

/**
 * Installe un CFRunLoopObserver sur le run loop courant pour :
 * 1. Dispatcher WindowEvent.RedrawRequested (GRA-134) pour les fenêtres en attente
 * 2. Appeler ApplicationHandler.aboutToWait(eventLoop) (GRA-135) — après les redraws
 *
 * Doit être créé et installé depuis le thread principal, avant app.run().
 */
internal class CFRunLoopRedrawObserver(
    private val handler: ApplicationHandler,
    private val eventLoop: ActiveEventLoop,
    private val windows: ConcurrentHashMap<Long, AppKitWindow>,
) {
    init {
        // Register this instance globally so the static callback can find it
        instance = this
    }

    /**
     * Appelé par le CFRunLoopObserver avant chaque mise en veille du run loop.
     *
     * Ordre garanti par la spec :
     * 1. Dispatch [WindowEvent.RedrawRequested] pour chaque fenêtre avec needsRedraw=true (GRA-134)
     * 2. Appel [ApplicationHandler.aboutToWait] après tous les redraws (GRA-135)
     *
     * Ne dispatch pas si [ActiveEventLoop.isExiting] — évite des callbacks parasites
     * entre [ActiveEventLoop.exit] et la sortie effective du run loop.
     */
    fun onBeforeWaiting() {
        if (eventLoop.isExiting) return

        windows.values.forEach { window ->
            if (window.needsRedraw) {
                window.needsRedraw = false
                handler.windowEvent(eventLoop, window.id, WindowEvent.RedrawRequested)
            }
        }

        // aboutToWait dispatché après tous les RedrawRequested (GRA-135)
        handler.aboutToWait(eventLoop)
    }

    companion object {
        /** Instance singleton — un seul observer par application. */
        @Volatile
        private var instance: CFRunLoopRedrawObserver? = null

        /** kCFRunLoopBeforeWaiting activity bit */
        private const val kCFRunLoopBeforeWaiting = 0x20L

        /**
         * Crée et installe l'observer sur le run loop courant.
         * Retourne l'observer (retenu dans Arena.global()).
         */
        fun install(
            handler: ApplicationHandler,
            eventLoop: ActiveEventLoop,
            windows: ConcurrentHashMap<Long, AppKitWindow>,
        ): CFRunLoopRedrawObserver {
            val observer = CFRunLoopRedrawObserver(handler, eventLoop, windows)

            val arena = Arena.global()
            val linker = Linker.nativeLinker()
            val lookup = MethodHandles.lookup()

            // Load CoreFoundation
            val cfLib: SymbolLookup = SymbolLookup.loaderLookup().let { loader ->
                if (loader.find("CFRunLoopGetCurrent").isPresent) loader
                else SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena
                )
            }

            // Resolve CF functions
            val cfRunLoopGetCurrent = cfLib.find("CFRunLoopGetCurrent").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopGetCurrent not found")
            }
            val cfRunLoopObserverCreate = cfLib.find("CFRunLoopObserverCreate").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopObserverCreate not found")
            }
            val cfRunLoopAddObserver = cfLib.find("CFRunLoopAddObserver").orElseThrow {
                UnsatisfiedLinkError("CFRunLoopAddObserver not found")
            }

            // kCFRunLoopCommonModes global constant (CFStringRef)
            val kCFRunLoopCommonModesPtr = cfLib.find("kCFRunLoopCommonModes").orElseThrow {
                UnsatisfiedLinkError("kCFRunLoopCommonModes not found")
            }
            // Dereference: the symbol IS a pointer to the CFStringRef, not the CFStringRef itself
            val kCFRunLoopCommonModes: MemorySegment = kCFRunLoopCommonModesPtr
                .reinterpret(8L)
                .get(ValueLayout.ADDRESS, 0L)

            // Upcall stub: void callback(CFRunLoopObserverRef, CFRunLoopActivity, void*)
            val callbackHandle = lookup.findStatic(
                CFRunLoopRedrawObserver::class.java,
                "redrawCallback",
                MethodType.methodType(
                    Void.TYPE,
                    MemorySegment::class.java, // observer
                    Long::class.javaPrimitiveType, // activity (CFRunLoopActivity = CFOptionFlags = long)
                    MemorySegment::class.java, // info
                ),
            )
            val callbackStub = linker.upcallStub(
                callbackHandle,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,  // observer
                    ValueLayout.JAVA_LONG, // activity
                    ValueLayout.ADDRESS,  // info
                ),
                arena,
            )

            // CFRunLoopGetCurrent()
            val getCurrentHandle = linker.downcallHandle(
                cfRunLoopGetCurrent,
                FunctionDescriptor.of(ValueLayout.ADDRESS),
            )
            val runLoop = getCurrentHandle.invokeExact() as MemorySegment

            // CFRunLoopObserverCreate(allocator, activities, repeats, order, callout, context)
            val createHandle = linker.downcallHandle(
                cfRunLoopObserverCreate,
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS,   // return: CFRunLoopObserverRef
                    ValueLayout.ADDRESS,   // allocator (NULL = kCFAllocatorDefault)
                    ValueLayout.JAVA_LONG, // activities (CFOptionFlags)
                    ValueLayout.JAVA_BYTE, // repeats (Boolean = unsigned char)
                    ValueLayout.JAVA_LONG, // order (CFIndex)
                    ValueLayout.ADDRESS,   // callout
                    ValueLayout.ADDRESS,   // context (NULL)
                ),
            )
            val observerRef = createHandle.invoke(
                MemorySegment.NULL,          // allocator
                kCFRunLoopBeforeWaiting,     // activities
                1.toByte(),                  // repeats = true
                0L,                          // order
                callbackStub,                // callout
                MemorySegment.NULL,          // context
            ) as MemorySegment

            // CFRunLoopAddObserver(runLoop, observer, kCFRunLoopCommonModes)
            val addHandle = linker.downcallHandle(
                cfRunLoopAddObserver,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // rl
                    ValueLayout.ADDRESS, // rlo
                    ValueLayout.ADDRESS, // mode
                ),
            )
            addHandle.invokeExact(runLoop, observerRef, kCFRunLoopCommonModes)

            return observer
        }

        /** Static trampoline invoked by the upcall stub from CoreFoundation. */
        @JvmStatic
        fun redrawCallback(
            @Suppress("UNUSED_PARAMETER") observerRef: MemorySegment,
            @Suppress("UNUSED_PARAMETER") activity: Long,
            @Suppress("UNUSED_PARAMETER") info: MemorySegment,
        ) {
            instance?.onBeforeWaiting()
        }
    }
}

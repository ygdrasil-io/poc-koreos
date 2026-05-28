/**
 * Implémentation Kotlin de `NSWindowDelegate` via subclassing ObjC runtime.
 *
 * Utilise les upcall stubs de Panama FFM pour exposer des fonctions statiques
 * Kotlin (`@JvmStatic`) comme implémentations de méthodes Objective-C.
 *
 * Stratégie de dispatch : la classe ObjC `KoreosWindowDelegateNative` n'embarque pas
 * de pointeur vers le delegate Kotlin. À la place, on indexe les instances dans
 * une [java.util.concurrent.ConcurrentHashMap] globale dont la clé est l'adresse
 * mémoire (`MemorySegment.address()`) du `self` ObjC. Le premier argument de
 * tout upcall ObjC étant `self`, cela suffit à retrouver l'instance Kotlin
 * cible lors d'un callback natif.
 *
 * GRA-127 : dispatch de WindowEvent.CloseRequested vers ApplicationHandler.
 */
package io.ygdrasil.koreos.appkit

import io.ygdrasil.koreos.appkit.bindings.ObjCRuntime
import io.ygdrasil.koreos.core.ActiveEventLoop
import io.ygdrasil.koreos.core.ApplicationHandler
import io.ygdrasil.koreos.core.WindowEvent
import io.ygdrasil.koreos.core.WindowId
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

/**
 * Delegate de fenêtre macOS implémentant `NSWindowDelegate` via FFM.
 *
 * Intercepte `windowShouldClose:` pour dispatcher [WindowEvent.CloseRequested]
 * vers l'[ApplicationHandler]. Retourne `false` (BOOL = 0) afin que l'application
 * contrôle la fermeture via [ActiveEventLoop.exit].
 *
 * @param handler    Gestionnaire d'application recevant les événements.
 * @param eventLoop  Boucle d'événements active au moment de la création du delegate.
 * @param windowId   Identifiant de la fenêtre surveillée.
 */
class KoreosWindowDelegate(
    private val handler: ApplicationHandler,
    private val eventLoop: ActiveEventLoop,
    private val windowId: WindowId,
) {
    /** Pointeur vers l'objet Objective-C wrappé par ce délégué. */
    val ptr: MemorySegment

    init {
        ensureClassRegistered()

        val cls = ObjCRuntime.getClass("KoreosWindowDelegateNative")
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

        delegateTable[ptr.address()] = this
    }

    /**
     * Callback Kotlin pour `windowShouldClose:`.
     *
     * Dispatche [WindowEvent.CloseRequested] vers le gestionnaire. Si [eventLoop.exit]
     * a été appelé durant le callback, déclenche `[NSApp terminate:nil]` pour
     * arrêter la boucle AppKit. Retourne `0` (BOOL NO) — la fermeture reste sous
     * contrôle de l'application.
     */
    fun onWindowShouldClose(): Byte {
        handler.windowEvent(eventLoop, windowId, WindowEvent.CloseRequested)
        if (eventLoop.isExiting) {
            val nsAppClass = ObjCRuntime.getClass("NSApplication")
            val nsApp = ObjCRuntime.msgSend(
                ValueLayout.ADDRESS,
                nsAppClass,
                ObjCRuntime.sel("sharedApplication"),
            ) as MemorySegment
            ObjCRuntime.msgSend(null, nsApp, ObjCRuntime.sel("terminate:"), MemorySegment.NULL)
        }
        return 0 // NO — l'application contrôle la fermeture via exit()
    }

    companion object {
        /** Table globale : adresse mémoire ObjC → delegate Kotlin associé. */
        private val delegateTable = ConcurrentHashMap<Long, KoreosWindowDelegate>()

        @Volatile
        private var classRegistered: Boolean = false

        @Synchronized
        internal fun ensureClassRegistered() {
            if (classRegistered) return

            val arena = Arena.global()
            val linker: Linker = Linker.nativeLinker()
            val lookup = MethodHandles.lookup()

            val cls = ObjCSubclassing.allocateClass("NSObject", "KoreosWindowDelegateNative")
            ObjCSubclassing.addProtocol(cls, "NSWindowDelegate")

            // BOOL windowShouldClose(id self, SEL _cmd, id sender)
            // Encoding : "c@:@" — BOOL est signed char (c) sur macOS 64-bit ARM
            val windowShouldCloseHandle = lookup.findStatic(
                Callbacks::class.java,
                "windowShouldClose",
                MethodType.methodType(
                    java.lang.Byte.TYPE,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            )
            val windowShouldCloseStub = linker.upcallStub(
                windowShouldCloseHandle,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_BYTE,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                arena,
            )

            ObjCSubclassing.addMethod(
                cls,
                "windowShouldClose:",
                windowShouldCloseStub,
                "c@:@",
            )

            ObjCSubclassing.registerClass(cls)
            classRegistered = true
        }
    }

    /**
     * Trampolines `@JvmStatic` invoqués par les upcall stubs Panama.
     *
     * Les méthodes statiques sont indispensables : `Linker.upcallStub` ne sait
     * pas lier de méthodes d'instance car le `self` ObjC est passé en premier
     * argument, pas via le receiver Java.
     */
    object Callbacks {
        @JvmStatic
        fun windowShouldClose(
            self: MemorySegment,
            @Suppress("UNUSED_PARAMETER") cmd: MemorySegment,
            @Suppress("UNUSED_PARAMETER") sender: MemorySegment,
        ): Byte {
            // Si aucun delegate Kotlin n'est enregistré pour ce self, retourner YES (1)
            // pour permettre la fermeture par défaut.
            return delegateTable[self.address()]?.onWindowShouldClose() ?: 1
        }
    }
}

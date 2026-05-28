/**
 * Sous-classe Objective-C de NSApplication pour koreos.
 *
 * Pour M1 : enregistre simplement la sous-classe sans override
 * (équivalent du `KoreosApplication` Rust dans winit). M3 ajoutera
 * l'override de `sendEvent:` pour intercepter les événements clavier/souris.
 */
package io.ygdrasil.koreos.appkit

import io.ygdrasil.koreos.appkit.bindings.NSApplication
import io.ygdrasil.koreos.appkit.bindings.NSApplicationActivationPolicy
import io.ygdrasil.koreos.appkit.bindings.ObjCRuntime
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

class KoreosApplication private constructor(ptr: MemorySegment) : NSApplication(ptr) {

    /**
     * Définit la politique d'activation de l'application
     * (par défaut : `NSApplicationActivationPolicyRegular`).
     */
    fun setActivationPolicyRegular() {
        ObjCRuntime.msgSend(
            ValueLayout.JAVA_BOOLEAN,
            ptr,
            ObjCRuntime.sel("setActivationPolicy:"),
            NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular.value,
        )
    }

    companion object {
        /** Initialise la sous-classe ObjC une seule fois. */
        private val klass: MemorySegment by lazy {
            val cls = ObjCSubclassing.allocateClass("NSApplication", "KoreosApplication")
            ObjCSubclassing.registerClass(cls)
            cls
        }

        /**
         * Crée (ou récupère) l'instance unique partagée de `KoreosApplication`.
         *
         * Doit être appelé depuis le thread principal — l'invariant est validé
         * via [MainThreadCheck].
         */
        fun initialize(): KoreosApplication {
            MainThreadCheck.require()
            // Force la registration de la sous-classe avant le sharedApplication.
            klass
            val appClass = ObjCRuntime.getClass("KoreosApplication")
            val sharedApp = ObjCRuntime.msgSend(
                ValueLayout.ADDRESS,
                appClass,
                ObjCRuntime.sel("sharedApplication"),
            ) as MemorySegment
            return KoreosApplication(sharedApp)
        }
    }
}

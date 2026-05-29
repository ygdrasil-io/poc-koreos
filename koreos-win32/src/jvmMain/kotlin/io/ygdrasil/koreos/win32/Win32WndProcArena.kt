package io.ygdrasil.koreos.win32

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout

/**
 * Arena partagée à durée de vie processus pour les upcall stubs WndProc Win32.
 *
 * ⚠️ Ne JAMAIS fermer cette arena : les upcall stubs WndProc ont la durée de vie
 * du processus. Fermer l'arena invaliderait les stubs et provoquerait un SIGSEGV
 * lors du dispatch des messages Windows (WM_NCDESTROY tardif).
 *
 * Usage : `Win32WndProcArena.arena`
 */
object Win32WndProcArena {

    /**
     * Arena.ofShared() partagée entre tous les stubs WndProc du processus.
     * Jamais fermée intentionnellement.
     */
    val arena: Arena = Arena.ofShared()

    /**
     * FunctionDescriptor pour un WndProc Win32 :
     * LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM)
     */
    val wndProcDescriptor: FunctionDescriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_LONG,  // LRESULT
        ValueLayout.JAVA_LONG,  // HWND
        ValueLayout.JAVA_INT,   // UINT (message)
        ValueLayout.JAVA_LONG,  // WPARAM
        ValueLayout.JAVA_LONG   // LPARAM
    )
}

package org.graphiks.kadre.ffi.posix

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup

/** Resolves process symbols before trying optional Linux libc names. */
object PosixSymbols {
    private val lookup: SymbolLookup by lazy {
        val loader = SymbolLookup.loaderLookup()
        val libc = sequenceOf("libc.so.6", "libc.so")
            .mapNotNull { name ->
                runCatching { SymbolLookup.libraryLookup(name, Arena.global()) }.getOrNull()
            }
            .firstOrNull()

        if (libc == null) {
            loader
        } else {
            SymbolLookup { symbol -> loader.find(symbol).or { libc.find(symbol) } }
        }
    }

    fun find(name: String): MemorySegment? = lookup.find(name).orElse(null)
}

internal fun interface PosixSymbolLookup {
    fun find(name: String): MemorySegment?
}

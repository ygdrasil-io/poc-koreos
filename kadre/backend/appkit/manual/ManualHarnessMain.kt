package org.graphiks.kadre.internal.appkit.manual

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain

/**
 * Installs the process Main dispatcher these manual harnesses run under.
 *
 * The ownership-first process controller owns each session's root scope on
 * `Dispatchers.Main.immediate`; a bare JVM has no Main dispatcher, so the harness stands
 * one up on the Default pool — the native loop keeps owning its own thread exactly as it
 * always has. Call once at the top of a harness `main` before `runKadreApplication`.
 */
public fun installManualHarnessMainDispatcher() {
    Dispatchers.setMain(Dispatchers.Default)
}

/**
 * Proxy allowing interaction with the event loop from another thread.
 *
 * Scope: pure Kotlin interface, no native reference.
 */
package org.graphiks.kadre.core

/**
 * Thread-safe proxy to the main event loop.
 *
 * Allows secondary threads to wake up the event loop
 * without having direct access to it.
 */
interface EventLoopProxy {

    /**
     * Wakes a waiting loop. Calls are coalesced only until the loop consumes the wake-up;
     * subsequent calls must wake subsequent waits. Safe from any thread.
     */
    fun wakeUp()
}

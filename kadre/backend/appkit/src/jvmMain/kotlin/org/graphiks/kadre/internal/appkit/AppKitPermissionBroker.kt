package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred

/** Process-wide owner of Input Monitoring probes and a single shared system request. */
internal class AppKitPermissionBroker(
    private val native: AppKitRawInputNative,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lock = Any()
    private var requestInFlight: CompletableDeferred<Boolean>? = null

    fun preflightGranted(): Boolean = native.preflightPermission()

    /**
     * Joins the one in-flight native request. Cancelling an individual waiter never cancels the
     * process-wide prompt.
     */
    suspend fun requestPermission(): Boolean {
        if (preflightGranted()) return true
        val shared = synchronized(lock) {
            requestInFlight ?: CompletableDeferred<Boolean>().also { decision ->
                requestInFlight = decision
                scope.launch {
                    val granted = runCatching { native.requestPermission() }.getOrDefault(false)
                    decision.complete(granted)
                    synchronized(lock) {
                        if (requestInFlight === decision) requestInFlight = null
                    }
                }
            }
        }
        return shared.await()
    }
}

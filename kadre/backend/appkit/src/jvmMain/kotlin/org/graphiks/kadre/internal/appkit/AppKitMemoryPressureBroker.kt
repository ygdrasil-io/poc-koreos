package org.graphiks.kadre.internal.appkit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.graphiks.kadre.application.MemoryPressureLevel
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadrePlatform
import org.graphiks.kffi.objc.appkit.DispatchMemoryPressureEvent
import org.graphiks.kffi.objc.appkit.DispatchMemoryPressureSource

/** Pointer-free native source for process-wide memory pressure observations. */
internal interface AppKitMemoryPressureNative {
    fun open(listener: (MemoryPressureLevel) -> Unit): AutoCloseable
}

/** Schedules native source events away from the native callback stack. */
internal interface AppKitMemoryPressureDispatcher : AutoCloseable {
    fun dispatch(task: () -> Unit)
}

private class CoroutineAppKitMemoryPressureDispatcher : AppKitMemoryPressureDispatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun dispatch(task: () -> Unit) {
        scope.launch { task() }
    }

    override fun close() {
        scope.cancel()
    }
}

/** Process-wide source owner with explicit capability admission. */
internal class AppKitMemoryPressureBroker(
    private val native: AppKitMemoryPressureNative,
    private val deliver: (MemoryPressureLevel) -> Unit,
    private val dispatcher: AppKitMemoryPressureDispatcher = CoroutineAppKitMemoryPressureDispatcher(),
) : AutoCloseable {
    private val lock = Any()
    private var availability: FeatureAvailability? = null
    private var source: AutoCloseable? = null
    private var openingLevels: MutableList<MemoryPressureLevel>? = null
    private var closed = false

    /** Starts the native source once and returns its observed availability. */
    fun activate(): FeatureAvailability {
        val deliveredDuringOpen = mutableListOf<MemoryPressureLevel>()
        val result = synchronized(lock) {
            if (closed) return FeatureAvailability.Unsupported
            availability?.let { return it }
            openingLevels = deliveredDuringOpen
            try {
                source = native.open(::acceptNativePressure)
                FeatureAvailability.Available.also { availability = it }
            } catch (_: Exception) {
                unavailable().also { availability = it }
            } catch (_: LinkageError) {
                unavailable().also { availability = it }
            } finally {
                openingLevels = null
            }
        }
        if (result == FeatureAvailability.Available) {
            deliveredDuringOpen.forEach(::schedulePressure)
        }
        return result
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            source.also { source = null }
        }
        try {
            toClose?.close()
        } finally {
            dispatcher.close()
        }
    }

    private fun acceptNativePressure(level: MemoryPressureLevel) {
        val dispatch = synchronized(lock) {
            when {
                closed -> false
                availability == FeatureAvailability.Available -> true
                else -> {
                    openingLevels?.add(level)
                    false
                }
            }
        }
        if (!dispatch) return
        schedulePressure(level)
    }

    private fun schedulePressure(level: MemoryPressureLevel) {
        dispatcher.dispatch {
            if (synchronized(lock) { !closed && availability == FeatureAvailability.Available }) {
                deliver(level)
            }
        }
    }

    private fun unavailable(): FeatureAvailability.Unavailable = FeatureAvailability.Unavailable(
        KadreFailure.PlatformFailure(KadrePlatform.AppKit, "memory-pressure", "source-exception"),
    )
}

/** KFFI-backed Dispatch source. No native pointer or callback escapes this adapter. */
internal object KffiAppKitMemoryPressureNative : AppKitMemoryPressureNative {
    override fun open(listener: (MemoryPressureLevel) -> Unit): AutoCloseable =
        DispatchMemoryPressureSource { event ->
            listener(
                when (event) {
                    DispatchMemoryPressureEvent.WARN -> MemoryPressureLevel.Moderate
                    DispatchMemoryPressureEvent.CRITICAL -> MemoryPressureLevel.Critical
                },
            )
        }
}

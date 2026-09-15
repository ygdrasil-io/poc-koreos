package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.policy.ResourceBudgetPolicy
import org.graphiks.kadre.window.WindowProperty

/** Creates one private AppKit window-runtime composition for one future session adapter. */
internal class AppKitWindowRuntimeDriverFactory(
    private val nativePortFactory: () -> AppKitNativeWindowPort = { KffiAppKitWindowPort() },
) {
    fun create(
        resources: ResourceBudgetPolicy,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
        publicAppKitCapabilities: Boolean = false,
        enabledWindowUpdateCapabilities: Set<WindowProperty> = emptySet(),
        fullscreenAvailabilityFailure: KadreFailure.PlatformFailure? = null,
        publicSurfaceCapabilities: Boolean = false,
        onLastWindowClosed: (() -> Unit)? = null,
        beforeCommitDelivery: (org.graphiks.kadre.window.WindowSpec) -> Unit = { },
        beforeRuntimeSurfaceReadyDrain: () -> Unit = { },
        beforeFullscreenFollowUpEnqueue: (AppKitFullscreenCallback) -> Unit = { },
        captureSurfaceRegistry: AppKitCaptureSurfaceRegistry? = null,
        broker: AppKitProcessBroker? = null,
        attentionOwner: AppKitProcessBroker.AppKitUserAttentionOwner? = null,
        activateProcessOnFirstPresentation: Boolean = false,
    ): AppKitWindowRuntimeDriver {
        val nativePort = nativePortFactory()
        if (activateProcessOnFirstPresentation) nativePort.armProcessActivationOnFirstPresentation()
        return AppKitWindowRuntimeDriver(
            resources = resources,
            nativePort = nativePort,
            failureReporter = failureReporter,
            publicAppKitCapabilities = publicAppKitCapabilities,
            enabledWindowUpdateCapabilities = enabledWindowUpdateCapabilities,
            fullscreenAvailabilityFailure = fullscreenAvailabilityFailure,
            publicSurfaceCapabilities = publicSurfaceCapabilities,
            onLastWindowClosed = onLastWindowClosed,
            beforeCommitDelivery = beforeCommitDelivery,
            beforeRuntimeSurfaceReadyDrain = beforeRuntimeSurfaceReadyDrain,
            beforeFullscreenFollowUpEnqueue = beforeFullscreenFollowUpEnqueue,
            captureSurfaceRegistry = captureSurfaceRegistry,
            broker = broker,
            attentionOwner = attentionOwner,
        )
    }
}

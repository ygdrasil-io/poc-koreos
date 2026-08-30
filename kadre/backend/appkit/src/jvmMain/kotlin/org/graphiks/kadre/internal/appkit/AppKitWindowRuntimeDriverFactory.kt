package org.graphiks.kadre.internal.appkit

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
        publicSurfaceCapabilities: Boolean = false,
        onLastWindowClosed: (() -> Unit)? = null,
        beforeCommitDelivery: (org.graphiks.kadre.window.WindowSpec) -> Unit = { },
    ): AppKitWindowRuntimeDriver = AppKitWindowRuntimeDriver(
        resources = resources,
        nativePort = nativePortFactory(),
        failureReporter = failureReporter,
        publicAppKitCapabilities = publicAppKitCapabilities,
        enabledWindowUpdateCapabilities = enabledWindowUpdateCapabilities,
        publicSurfaceCapabilities = publicSurfaceCapabilities,
        onLastWindowClosed = onLastWindowClosed,
        beforeCommitDelivery = beforeCommitDelivery,
    )
}

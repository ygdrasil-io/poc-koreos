package org.graphiks.kadre.internal.appkit

import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.policy.ResourceBudgetPolicy

/** Creates one private AppKit window-runtime composition for one future session adapter. */
internal class AppKitWindowRuntimeDriverFactory(
    private val nativePortFactory: () -> AppKitNativeWindowPort = { KffiAppKitWindowPort() },
) {
    fun create(
        resources: ResourceBudgetPolicy,
        failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
    ): AppKitWindowRuntimeDriver = AppKitWindowRuntimeDriver(
        resources = resources,
        nativePort = nativePortFactory(),
        failureReporter = failureReporter,
    )
}

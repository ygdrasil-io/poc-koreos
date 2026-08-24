package org.graphiks.kadre.platform.desktop

import org.graphiks.kadre.internal.runtime.desktop.DesktopBackendProvider
import java.util.ServiceLoader

internal fun interface DesktopProviderCatalog {
    fun providers(): List<DesktopBackendProvider>
}

internal class LazyDesktopProviderCatalog(
    loader: () -> List<DesktopBackendProvider>,
) : DesktopProviderCatalog {
    private val cached: List<DesktopBackendProvider> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loader().toList()
    }

    override fun providers(): List<DesktopBackendProvider> = cached
}

internal object ServiceLoaderDesktopProviderCatalog : DesktopProviderCatalog by LazyDesktopProviderCatalog({
    val classLoader = Thread.currentThread().contextClassLoader
        ?: DesktopBackendProvider::class.java.classLoader
    ServiceLoader.load(DesktopBackendProvider::class.java, classLoader).toList()
})

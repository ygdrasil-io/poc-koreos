package org.graphiks.kadre.samples.compose.showcase

import org.graphiks.kadre.samples.compose.showcase.FeatureCategory

actual class PlatformContext {
    actual fun isFeatureSupported(category: FeatureCategory): Boolean = true
}

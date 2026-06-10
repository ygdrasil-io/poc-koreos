package org.graphiks.kadre.samples.compose.showcase

import org.graphiks.kadre.samples.compose.showcase.FeatureCategory

expect class PlatformContext {
    fun isFeatureSupported(category: FeatureCategory): Boolean
}

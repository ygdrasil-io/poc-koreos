package org.graphiks.kadre.core

/**
 * Capabilities of the Input Method Editor (IME) available on the platform.
 *
 * Returned by [Window.ime_capabilities] to allow applications to detect
 * dynamically the IME capabilities available on the current platform.
 *
 * @since R5-IME
 */
data class ImeCapabilities(
    /** Whether IME is enabled on this platform. */
    val enabled: Boolean,
    /** List of supported IME purposes. */
    val purposes: List<ImePurpose>,
    /** Additional platform-specific capabilities as bit flags. */
    val capabilities: Int = 0,
) {
    companion object {
        /** Default IME capabilities for platforms without IME support. */
        val NONE = ImeCapabilities(
            enabled = false,
            purposes = emptyList(),
            capabilities = 0,
        )
    }
}

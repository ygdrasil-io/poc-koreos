package org.graphiks.kadre.core

data class ImeCapabilities(
    val enabled: Boolean = false,
    val purposes: List<ImePurpose> = emptyList(),
    val capabilities: Set<ImeCapability> = emptySet(),
)

enum class ImeCapability {
    Composition,
    Provisional,
    Learning,
    Password,
    Terminal,
}

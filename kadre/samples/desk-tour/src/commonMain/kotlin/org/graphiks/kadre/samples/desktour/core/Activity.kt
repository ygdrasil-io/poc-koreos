package org.graphiks.kadre.samples.desktour.core

// Import explicite requis hors JVM : `kotlin.jvm.*` n'est pas un import par défaut sur wasmJs.
import kotlin.jvm.JvmInline

@JvmInline
internal value class ActionCorrelationId(val value: Long)

internal enum class ActivityStatus { Pending, Succeeded, Rejected, Cancelled, Unavailable }

internal data class ActivityEntry(
    val correlationId: ActionCorrelationId,
    val label: String,
    val status: ActivityStatus,
    val motif: String? = null,
    val apiDetail: String? = null,
)

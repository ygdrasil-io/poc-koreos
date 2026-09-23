package org.graphiks.kadre.samples.desktour.core

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

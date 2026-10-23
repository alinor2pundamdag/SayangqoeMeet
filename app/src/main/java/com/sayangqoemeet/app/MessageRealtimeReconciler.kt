package com.sayangqoemeet.app

/**
 * Reconcile a freshly loaded message page with messages already in memory.
 * Preserve older paginated rows, replace stale versions of refreshed rows,
 * and remove remote hard/soft deletes by the event's message identity.
 */
internal fun isMessageDeletionEvent(
    eventType: String,
    affectedId: String?,
    deletedAt: String?
): Boolean {
    val type = eventType.uppercase()
    val hasId = !affectedId.isNullOrBlank() && affectedId != "null"
    val isSoftDelete = type == "UPDATE" && !deletedAt.isNullOrBlank() && deletedAt != "null"
    return hasId && (type == "DELETE" || isSoftDelete)
}

internal fun reconcileRealtimeMessages(
    existing: List<MessageRecord>,
    fresh: List<MessageRecord>,
    eventType: String,
    affectedId: String?,
    deletedAt: String?
): List<MessageRecord> {
    val retained = if (isMessageDeletionEvent(eventType, affectedId, deletedAt)) {
        existing.filterNot { it.id == affectedId }
    } else {
        existing
    }
    val freshIds = fresh.asSequence().map { it.id }.toHashSet()
    return (retained.filterNot { it.id in freshIds } + fresh)
        .distinctBy { it.id }
        .sortedWith(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
}

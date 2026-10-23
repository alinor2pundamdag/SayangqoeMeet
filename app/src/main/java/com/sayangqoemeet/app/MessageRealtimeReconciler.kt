package com.sayangqoemeet.app

import org.json.JSONObject

/**
 * Reconcile a freshly loaded message page with the messages already in memory.
 * Preserve older paginated rows, replace stale versions of rows returned by the
 * server, and remove remote hard/soft deletes by their event identity.
 */
internal fun reconcileRealtimeMessages(
    existing: List<MessageRecord>,
    fresh: List<MessageRecord>,
    eventType: String,
    record: JSONObject,
    oldRecord: JSONObject
): List<MessageRecord> {
    val normalizedType = eventType.uppercase()
    val affected = if (normalizedType == "DELETE") oldRecord else record
    val affectedId = affected.optString("id").takeIf { it.isNotBlank() && it != "null" }
    val deletedAt = affected.optString("deleted_at")
    val isDeletion = normalizedType == "DELETE" ||
        (normalizedType == "UPDATE" && deletedAt.isNotBlank() && deletedAt != "null")

    val retained = if (isDeletion && affectedId != null) {
        existing.filterNot { it.id == affectedId }
    } else {
        existing
    }
    val freshIds = fresh.asSequence().map { it.id }.toHashSet()
    return (retained.filterNot { it.id in freshIds } + fresh)
        .distinctBy { it.id }
        .sortedWith(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
}

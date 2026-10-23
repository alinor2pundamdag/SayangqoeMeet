package com.sayangqoemeet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRealtimeReconcilerTest {
    private fun message(id: String, body: String, createdAt: String = "2026-10-23T10:00:00Z") =
        MessageRecord(
            id = id,
            conversationId = "conversation-1",
            senderId = "user-1",
            body = body,
            createdAt = createdAt
        )

    @Test
    fun hardDeleteEventRemovesTheOldMessage() {
        val existing = listOf(message("older", "keep", "2026-10-22T10:00:00Z"), message("deleted", "remove"))
        val result = reconcileRealtimeMessages(existing, emptyList(), "DELETE", "deleted", null)
        assertEquals(listOf("older"), result.map { it.id })
    }

    @Test
    fun softDeleteUpdateRemovesMessageEvenWhenFreshQueryOmitsIt() {
        val existing = listOf(message("deleted", "remove"), message("keep", "keep", "2026-10-22T10:00:00Z"))
        val result = reconcileRealtimeMessages(existing, emptyList(), "UPDATE", "deleted", "2026-10-23T10:05:00Z")
        assertEquals(listOf("keep"), result.map { it.id })
    }

    @Test
    fun freshServerRowsReplaceStaleRowsButPreserveOlderPagination() {
        val existing = listOf(
            message("older", "older page", "2026-10-20T10:00:00Z"),
            message("same", "stale body", "2026-10-23T10:00:00Z")
        )
        val fresh = listOf(message("same", "server body", "2026-10-23T10:00:00Z"), message("new", "new message", "2026-10-23T10:01:00Z"))
        val result = reconcileRealtimeMessages(existing, fresh, "INSERT", "new", null)
        assertEquals(listOf("older", "same", "new"), result.map { it.id })
        assertEquals("server body", result.single { it.id == "same" }.body)
    }

    @Test
    fun deleteEventRequiresAnAffectedMessageIdentity() {
        assertFalse(isMessageDeletionEvent("DELETE", null, null))
        assertTrue(isMessageDeletionEvent("DELETE", "message-1", null))
        assertTrue(isMessageDeletionEvent("UPDATE", "message-1", "2026-10-23T10:05:00Z"))
        assertFalse(isMessageDeletionEvent("UPDATE", "message-1", "null"))
    }
}

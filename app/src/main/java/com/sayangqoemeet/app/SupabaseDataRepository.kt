package com.sayangqoemeet.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class ProfileRecord(
    val id: String,
    val username: String?,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String? = null,
    val birthday: String? = null,
    val religion: String? = null,
    val address: String? = null,
    val phoneNumber: String? = null
)

internal data class RegisteredUserRecord(
    val id: String,
    val username: String?,
    val displayName: String,
    val createdAt: String,
    val isOnline: Boolean = false
)

internal data class ConversationRecord(
    val id: String,
    val title: String?,
    val isGroup: Boolean,
    val createdAt: String,
    val otherUserId: String? = null,
    val otherUsername: String? = null,
    val otherDisplayName: String? = null,
    val displayLabel: String = "Private conversation"
)

internal data class MessageRecord(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: String
)

internal data class InvitationRecord(
    val id: String,
    val conversationId: String,
    val inviterId: String,
    val createdAt: String
)

/**
 * Small authenticated PostgREST/Edge Function adapter.
 * The access token comes from the active Supabase Auth session; only the
 * publishable key is used as the public apikey header.
 */
internal object SupabaseDataRepository {
    private fun userId(): String =
        SupabaseAuthRepository.currentUserId() ?: error("Your session is no longer available. Sign in again.")

    private fun request(method: String, path: String, body: JSONObject? = null, prefer: String? = null): String {
        val token = SupabaseAuthRepository.currentAccessToken()
            ?: error("Your session is no longer available. Sign in again.")
        val isFunction = path.startsWith("functions/v1/") || path.startsWith("rpc/")
        val base = BuildConfig.SUPABASE_URL.trimEnd('/') + if (isFunction) "/" else "/rest/v1/"
        val restBase = BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/"
        val functionBase = BuildConfig.SUPABASE_URL.trimEnd('/') + "/"
        val isEdgeFunction = path.startsWith("functions/v1/")
        val fullUrl = if (isEdgeFunction) functionBase + path else restBase + path
        val connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            if (prefer != null) setRequestProperty("Prefer", prefer)
        }
        try {
            if (body != null) {
                connection.outputStream.use { stream ->
                    stream.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = try {
                    JSONObject(response).optString("message").ifBlank {
                        JSONObject(response).optString("error").ifBlank { response }
                    }
                } catch (_: Exception) { response }
                error("Request failed ($code): ${detail.take(180)}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun runRequest(block: () -> String): String =
        withContext(Dispatchers.IO) { block() }

    suspend fun saveDeviceToken(token: String) {
        val payload = JSONObject()
            .put("user_id", userId())
            .put("token", token)
            .put("platform", "android")
            .put("updated_at", java.time.Instant.now().toString())
        runRequest {
            request("POST", "device_tokens?on_conflict=token", payload, "resolution=merge-duplicates,return=minimal")
        }
    }

    private fun profileFromRow(row: JSONObject): ProfileRecord = ProfileRecord(
        id = row.getString("id"),
        username = row.optString("username").takeIf { it.isNotBlank() },
        displayName = row.optString("display_name"),
        avatarUrl = row.optString("avatar_url").takeIf { it.isNotBlank() },
        bio = row.optString("bio").takeIf { it.isNotBlank() },
        birthday = row.optString("birthday").takeIf { it.isNotBlank() },
        religion = row.optString("religion").takeIf { it.isNotBlank() },
        address = row.optString("address").takeIf { it.isNotBlank() },
        phoneNumber = row.optString("phone_number").takeIf { it.isNotBlank() }
    )

    // Reads the signed-in user's own full profile, including the fields
    // (bio/birthday/religion/address/phone_number) that other users cannot
    // read directly, via the get_my_profile() SECURITY DEFINER RPC.
    suspend fun loadProfile(): ProfileRecord? {
        val json = runRequest { request("POST", "rpc/get_my_profile", JSONObject()) }
        val rows = JSONArray(json)
        if (rows.length() == 0) return null
        return profileFromRow(rows.getJSONObject(0))
    }

    suspend fun heartbeatPresence() {
        val id = userId()
        val payload = JSONObject()
            .put("user_id", id)
            .put("last_seen_at", java.time.Instant.now().toString())
        runRequest {
            request("POST", "user_presence?on_conflict=user_id", payload,
                "resolution=merge-duplicates,return=minimal")
        }
    }

    /**
     * Marks this account offline without requiring DELETE permission.
     * The user_presence RLS policy allows a user to update only their own row.
     */
    suspend fun clearPresence() {
        val id = userId()
        val payload = JSONObject()
            .put("last_seen_at", "1970-01-01T00:00:00Z")
        runRequest {
            request("PATCH", "user_presence?user_id=eq.${enc(id)}", payload,
                "return=minimal")
        }
    }

    private suspend fun loadOnlineUserIds(): Set<String> {
        val cutoff = java.time.Instant.now().minusSeconds(90).toString()
        val query = "user_presence?select=user_id&last_seen_at=gte.${enc(cutoff)}"
        val rows = JSONArray(runRequest { request("GET", query) })
        return (0 until rows.length()).map { rows.getJSONObject(it).getString("user_id") }.toSet()
    }

    suspend fun loadRegisteredUsers(): List<RegisteredUserRecord> {
        val id = userId()
        val query = "profiles?id=neq.${enc(id)}&select=id,username,display_name,created_at&order=created_at.desc&limit=500"
        val rows = JSONArray(runRequest { request("GET", query) })
        val onlineIds = runCatching { loadOnlineUserIds() }.getOrDefault(emptySet())
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val userId = row.getString("id")
            RegisteredUserRecord(
                id = userId,
                username = row.optString("username").takeIf { it.isNotBlank() },
                displayName = row.optString("display_name"),
                createdAt = row.optString("created_at"),
                isOnline = userId in onlineIds
            )
        }
    }

    suspend fun saveProfile(
        displayName: String,
        username: String,
        avatarUrl: String? = null,
        bio: String? = null,
        birthday: String? = null,
        religion: String? = null,
        address: String? = null,
        phoneNumber: String? = null
    ): ProfileRecord {
        val id = userId()
        val payload = JSONObject()
            .put("id", id)
            .put("display_name", displayName.trim())
            .put("username", username.trim())
            .put("updated_at", java.time.Instant.now().toString())
        payload.put("avatar_url", avatarUrl?.trim().takeUnless { it.isNullOrBlank() } ?: JSONObject.NULL)
        payload.put("bio", bio?.trim().takeUnless { it.isNullOrBlank() } ?: JSONObject.NULL)
        payload.put("birthday", birthday?.trim().takeUnless { it.isNullOrBlank() } ?: JSONObject.NULL)
        payload.put("religion", religion?.trim().takeUnless { it.isNullOrBlank() } ?: JSONObject.NULL)
        payload.put("address", address?.trim().takeUnless { it.isNullOrBlank() } ?: JSONObject.NULL)
        payload.put("phone_number", phoneNumber?.trim().takeUnless { it.isNullOrBlank() } ?: JSONObject.NULL)
        runRequest {
            request("POST", "profiles?on_conflict=id", payload, "resolution=merge-duplicates,return=minimal")
        }
        // The table-level SELECT grant no longer includes the sensitive columns
        // (see migration 20261018120000), so re-read the saved row through the
        // same RPC used by loadProfile() rather than the POST response body.
        return loadProfile() ?: error("Profile save appeared to succeed but could not be re-read.")
    }

    suspend fun openPrivateConversation(targetUserId: String): String {
        require(targetUserId.isNotBlank()) { "Select a valid user to message." }
        val payload = JSONObject().put("target_user_id", targetUserId)
        val response = runRequest { request("POST", "functions/v1/open-private-conversation", payload) }
        return JSONObject(response).getString("conversation_id")
    }

    suspend fun createConversation(recipientUsername: String): InvitationRecord {
        val payload = JSONObject().put("recipient_username", recipientUsername.trim())
        val response = runRequest { request("POST", "functions/v1/create-conversation", payload) }
        val root = JSONObject(response)
        val conversation = root.getJSONObject("conversation")
        val invitation = root.getJSONObject("invitation")
        return InvitationRecord(
            id = invitation.getString("id"),
            conversationId = conversation.getString("id"),
            inviterId = userId(),
            createdAt = invitation.optString("created_at")
        )
    }

    suspend fun loadPendingInvitations(): List<InvitationRecord> {
        val id = userId()
        val query = "conversation_invites?recipient_id=eq.${enc(id)}&status=eq.pending&select=id,conversation_id,inviter_id,created_at&order=created_at.desc"
        val rows = JSONArray(runRequest { request("GET", query) })
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            InvitationRecord(
                id = row.getString("id"),
                conversationId = row.getString("conversation_id"),
                inviterId = row.getString("inviter_id"),
                createdAt = row.getString("created_at")
            )
        }
    }

    suspend fun respondToInvitation(invitationId: String, accept: Boolean) {
        val payload = JSONObject()
            .put("invitation_id", invitationId)
            .put("accept", accept)
        runRequest { request("POST", "functions/v1/respond-conversation-invite", payload) }
    }

    /**
     * Loads the authenticated user's conversations through a SECURITY DEFINER RPC.
     * The database enforces membership and resolves the other participant in one
     * query, avoiding missing nested PostgREST profile joins and literal "null" labels.
     */
    suspend fun loadConversations(): List<ConversationRecord> {
        val rows = JSONArray(runRequest { request("POST", "rpc/get_my_conversation_list", JSONObject()) })
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("conversation_id").trim()
            if (id.isBlank() || id == "null") return@mapNotNull null
            val isGroup = row.optBoolean("is_group", false)
            val title = row.optString("title").takeUnless { it.isBlank() || it == "null" }
            val otherUserId = row.optString("other_user_id").takeUnless { it.isBlank() || it == "null" }
            val otherUsername = row.optString("other_username").trim()
                .takeUnless { it.isBlank() || it == "null" }
            val otherDisplayName = row.optString("other_display_name").trim()
                .takeUnless { it.isBlank() || it == "null" }
            val serverLabel = row.optString("display_label").trim()
                .takeUnless { it.isBlank() || it == "null" }
            val fallback = if (isGroup) "Group conversation" else "Private conversation"
            val displayLabel = when {
                isGroup -> title ?: serverLabel ?: fallback
                else -> serverLabel ?: otherDisplayName ?: otherUsername?.let { "@$it" } ?: "SayangqoeMeet user"
            }
            ConversationRecord(
                id = id,
                title = title,
                isGroup = isGroup,
                createdAt = row.optString("created_at").takeUnless { it == "null" }.orEmpty(),
                otherUserId = otherUserId,
                otherUsername = otherUsername,
                otherDisplayName = otherDisplayName,
                displayLabel = displayLabel
            )
        }
    }

    suspend fun loadMessages(conversationId: String): List<MessageRecord> {
        val query = "messages?conversation_id=eq.${enc(conversationId)}&select=id,conversation_id,sender_id,body,created_at&order=created_at.asc&limit=100"
        val rows = JSONArray(runRequest { request("GET", query) })
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            MessageRecord(row.getString("id"), row.getString("conversation_id"), row.getString("sender_id"), row.getString("body"), row.getString("created_at"))
        }
    }

    suspend fun sendMessage(conversationId: String, body: String): MessageRecord {
        val payload = JSONObject()
            .put("conversation_id", conversationId)
            .put("sender_id", userId())
            .put("body", body.trim())
        val response = runRequest { request("POST", "messages", payload, "return=representation") }
        val row = JSONArray(response).getJSONObject(0)
        val saved = MessageRecord(row.getString("id"), row.getString("conversation_id"), row.getString("sender_id"), row.getString("body"), row.getString("created_at"))
        // Push delivery is best-effort: message persistence must not fail if Firebase is not configured.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                request(
                    "POST",
                    "functions/v1/send-message-push",
                    JSONObject().put("conversation_id", saved.conversationId).put("message_id", saved.id)
                )
            }
        }
        return saved
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

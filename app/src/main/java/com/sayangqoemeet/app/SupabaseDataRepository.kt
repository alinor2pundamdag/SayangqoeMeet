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
    val avatarUrl: String?
)

internal data class RegisteredUserRecord(
    val id: String,
    val username: String?,
    val displayName: String,
    val createdAt: String
)

internal data class ConversationRecord(
    val id: String,
    val title: String?,
    val isGroup: Boolean,
    val createdAt: String,
    val memberUsername: String? = null
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
        val isFunction = path.startsWith("functions/v1/")
        val base = BuildConfig.SUPABASE_URL.trimEnd('/') + if (isFunction) "/" else "/rest/v1/"
        val actualPath = if (isFunction) path else path
        val connection = (URL(base + actualPath).openConnection() as HttpURLConnection).apply {
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

    suspend fun loadProfile(): ProfileRecord? {
        val id = userId()
        val query = "profiles?id=eq.${enc(id)}&select=id,username,display_name,avatar_url&limit=1"
        val json = runRequest { request("GET", query) }
        val rows = JSONArray(json)
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        return ProfileRecord(row.getString("id"), row.optString("username").takeIf { it.isNotBlank() }, row.optString("display_name"), row.optString("avatar_url").takeIf { it.isNotBlank() })
    }

    suspend fun loadRegisteredUsers(): List<RegisteredUserRecord> {
        val id = userId()
        val query = "profiles?id=neq.${enc(id)}&select=id,username,display_name,created_at&order=created_at.desc&limit=500"
        val rows = JSONArray(runRequest { request("GET", query) })
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            RegisteredUserRecord(
                id = row.getString("id"),
                username = row.optString("username").takeIf { it.isNotBlank() },
                displayName = row.optString("display_name"),
                createdAt = row.optString("created_at")
            )
        }
    }

    suspend fun saveProfile(displayName: String, username: String): ProfileRecord {
        val id = userId()
        val payload = JSONObject()
            .put("id", id)
            .put("display_name", displayName.trim())
            .put("username", username.trim())
            .put("updated_at", java.time.Instant.now().toString())
        val response = runRequest {
            request("POST", "profiles?on_conflict=id", payload, "resolution=merge-duplicates,return=representation")
        }
        val row = JSONArray(response).getJSONObject(0)
        return ProfileRecord(row.getString("id"), row.optString("username").ifBlank { null }, row.optString("display_name"), row.optString("avatar_url").ifBlank { null })
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

    suspend fun loadConversations(): List<ConversationRecord> {
        val id = userId()
        val query = "conversation_members?user_id=eq.${enc(id)}&select=conversation_id,conversations(id,title,is_group,created_at)&order=joined_at.desc"
        val rows = JSONArray(runRequest { request("GET", query) })
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            val conversation = row.optJSONObject("conversations") ?: return@mapNotNull null
            ConversationRecord(
                id = conversation.optString("id"),
                title = conversation.optString("title").takeIf { it.isNotBlank() },
                isGroup = conversation.optBoolean("is_group"),
                createdAt = conversation.optString("created_at")
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

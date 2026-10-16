package com.sayangqoemeet.app

import kotlinx.coroutines.Dispatchers
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

    suspend fun loadProfile(): ProfileRecord? {
        val id = userId()
        val query = "profiles?id=eq.${enc(id)}&select=id,username,display_name,avatar_url&limit=1"
        val json = runRequest { request("GET", query) }
        val rows = JSONArray(json)
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        return ProfileRecord(row.getString("id"), row.optString("username").takeIf { it.isNotBlank() }, row.optString("display_name"), row.optString("avatar_url").takeIf { it.isNotBlank() })
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

    suspend fun createConversation(recipientUsername: String): ConversationRecord {
        val payload = JSONObject().put("recipient_username", recipientUsername.trim())
        val response = runRequest { request("POST", "functions/v1/create-conversation", payload) }
        val root = JSONObject(response)
        val conversation = root.getJSONObject("conversation")
        val recipient = root.optJSONObject("recipient")
        return ConversationRecord(
            id = conversation.getString("id"),
            title = recipient?.optString("display_name")?.ifBlank { recipient.optString("username") },
            isGroup = false,
            createdAt = conversation.optString("created_at"),
            memberUsername = recipient?.optString("username")
        )
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
        return MessageRecord(row.getString("id"), row.getString("conversation_id"), row.getString("sender_id"), row.getString("body"), row.getString("created_at"))
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

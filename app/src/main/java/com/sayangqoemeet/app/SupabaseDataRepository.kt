package com.sayangqoemeet.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    val isOnline: Boolean? = null,
    val avatarUrl: String? = null
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
    val createdAt: String,
    val attachmentPath: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentName: String? = null
)

internal data class InvitationRecord(
    val id: String,
    val conversationId: String,
    val inviterId: String,
    val createdAt: String
)

internal data class AdminDashboardOverview(
    val role: String,
    val generatedAt: String,
    val registeredProfiles: Int,
    val conversations: Int,
    val messages: Int,
    val activeCalls: Int,
    val activeUsersLast90Seconds: Int
)

internal data class AdminAuditEvent(
    val id: String,
    val actorId: String,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val details: String,
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
        require(token.isNotBlank()) { "FCM token is empty." }
        // Identity is derived server-side from auth.uid(); the RPC atomically
        // transfers this installation token after account switching.
        val payload = JSONObject()
            .put("p_token", token)
            .put("p_platform", "android")
        runRequest { request("POST", "rpc/register_device_token", payload) }
    }

    /**
     * Remove only this installation's token while the user's session is still
     * valid. The authenticated user filter limits cleanup to this user's row;
     * RLS remains the final authorization boundary.
     */
    suspend fun removeDeviceToken(token: String) {
        require(token.isNotBlank()) { "FCM token is empty." }
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        val encodedUserId = URLEncoder.encode(userId(), "UTF-8")
        runRequest {
            request(
                "DELETE",
                "device_tokens?user_id=eq.$encodedUserId&token=eq.$encodedToken",
                prefer = "return=minimal"
            )
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

    suspend fun uploadProfileAvatar(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val id = userId()
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "Choose a JPG, PNG, or WebP image." }
        val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the selected image.")
        require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) { "Profile photos must be smaller than 8 MB." }
        val objectPath = "$id/avatar.$extension"
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/profile-avatars/" + objectPath
        val token = SupabaseAuthRepository.currentAccessToken() ?: error("Your session is no longer available. Sign in again.")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", mime)
            setRequestProperty("x-upsert", "true")
        }
        try {
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = (connection.errorStream?.bufferedReader()?.use { it.readText() }).orEmpty()
                error("Profile photo upload failed ($code): ${detail.take(160)}")
            }
        } finally { connection.disconnect() }
        BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/public/profile-avatars/" + objectPath
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
        val query = "profiles?id=neq.${enc(id)}&select=id,username,display_name,created_at,avatar_url&order=created_at.desc&limit=500"
        val rows = JSONArray(runRequest { request("GET", query) })
        // Distinguish a confirmed offline result from a failed presence request.
        // A transport/RLS error must never be rendered as a definitive offline state.
        val onlineIds = runCatching { loadOnlineUserIds() }.getOrNull()
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val userId = row.getString("id")
            RegisteredUserRecord(
                id = userId,
                username = row.optString("username").takeIf { it.isNotBlank() },
                displayName = row.optString("display_name"),
                createdAt = row.optString("created_at"),
                isOnline = onlineIds?.let { userId in it },
                avatarUrl = row.optString("avatar_url").takeIf { it.isNotBlank() }
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

    private fun messageFromRow(row: JSONObject) = MessageRecord(
        id = row.getString("id"),
        conversationId = row.getString("conversation_id"),
        senderId = row.getString("sender_id"),
        body = row.optString("body"),
        createdAt = row.getString("created_at"),
        attachmentPath = row.optString("attachment_path").takeIf { it.isNotBlank() && it != "null" },
        attachmentMimeType = row.optString("attachment_mime_type").takeIf { it.isNotBlank() && it != "null" },
        attachmentName = row.optString("attachment_name").takeIf { it.isNotBlank() && it != "null" }
    )

    /**
     * Loads the newest page, then presents it oldest-to-newest for the chat UI.
     * Ordering by both timestamp and UUID makes the cursor deterministic when
     * multiple messages share the same timestamp.
     */
    suspend fun loadMessages(conversationId: String): List<MessageRecord> {
        require(conversationId.isNotBlank()) { "Select a valid conversation." }
        val query = "messages?conversation_id=eq.${enc(conversationId)}&deleted_at=is.null&select=id,conversation_id,sender_id,body,created_at,attachment_path,attachment_mime_type,attachment_name&order=created_at.desc,id.desc&limit=100"
        val rows = JSONArray(runRequest { request("GET", query) })
        return (0 until rows.length()).map { messageFromRow(rows.getJSONObject(it)) }.asReversed()
    }

    /** Fetches one older page using a stable (created_at, id) cursor. */
    suspend fun loadOlderMessages(conversationId: String, before: MessageRecord, pageSize: Int = 50): List<MessageRecord> {
        require(conversationId.isNotBlank()) { "Select a valid conversation." }
        require(pageSize in 1..100) { "Page size must be between 1 and 100." }
        val cursor = "(created_at.lt.${before.createdAt},and(created_at.eq.${before.createdAt},id.lt.${before.id}))"
        val query = "messages?conversation_id=eq.${enc(conversationId)}&deleted_at=is.null&or=${enc(cursor)}&select=id,conversation_id,sender_id,body,created_at,attachment_path,attachment_mime_type,attachment_name&order=created_at.desc,id.desc&limit=$pageSize"
        val rows = JSONArray(runRequest { request("GET", query) })
        return (0 until rows.length()).map { messageFromRow(rows.getJSONObject(it)) }.asReversed()
    }

    /** Soft-delete only the authenticated sender's own live message. */
    suspend fun softDeleteMessage(message: MessageRecord) {
        val uid = userId()
        require(message.senderId == uid) { "You can only delete messages you sent." }
        val payload = JSONObject().put("deleted_at", java.time.Instant.now().toString())
        val query = "messages?id=eq.${enc(message.id)}&sender_id=eq.${enc(uid)}&deleted_at=is.null"
        // The SELECT policy hides rows immediately after deleted_at is set, so
        // requesting a representation can return an empty array even on success.
        // Let PostgREST report authorization/trigger errors and use minimal return.
        runRequest {
            request("PATCH", query, payload, "return=minimal")
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        body: String,
        clientRequestId: String? = null
    ): MessageRecord {
        val senderId = userId()
        val cleanBody = body.trim()
        require(conversationId.isNotBlank()) { "Select a valid conversation." }
        require(cleanBody.isNotEmpty()) { "Message cannot be empty." }
        require(cleanBody.length <= 10_000) { "Messages must be 10,000 characters or fewer." }
        clientRequestId?.let { java.util.UUID.fromString(it) }

        val payload = JSONObject()
            .put("conversation_id", conversationId)
            .put("sender_id", senderId)
            .put("body", cleanBody)
        if (clientRequestId != null) payload.put("client_request_id", clientRequestId)

        val path = if (clientRequestId == null) {
            "messages?select=id,conversation_id,sender_id,body,created_at,attachment_path,attachment_mime_type,attachment_name"
        } else {
            "messages?on_conflict=sender_id,client_request_id&select=id,conversation_id,sender_id,body,created_at,attachment_path,attachment_mime_type,attachment_name"
        }
        val prefer = if (clientRequestId == null) {
            "return=representation"
        } else {
            "resolution=ignore-duplicates,return=representation"
        }
        val response = runRequest { request("POST", path, payload, prefer) }
        val rows = JSONArray(response)
        val saved = if (rows.length() > 0) {
            messageFromRow(rows.getJSONObject(0))
        } else {
            // A duplicate idempotency key returns no inserted row. Resolve the
            // original row so a retry after a lost HTTP response is still success.
            val key = clientRequestId ?: error("Message insert returned no row.")
            val lookup = "messages?sender_id=eq.${enc(senderId)}&client_request_id=eq.${enc(key)}&select=id,conversation_id,sender_id,body,created_at,attachment_path,attachment_mime_type,attachment_name&limit=1"
            val existingRows = JSONArray(runRequest { request("GET", lookup) })
            check(existingRows.length() == 1) {
                "Message retry could not be reconciled. Refresh the conversation before retrying."
            }
            val existing = messageFromRow(existingRows.getJSONObject(0))
            check(existing.conversationId == conversationId && existing.body == cleanBody) {
                "This retry key is already associated with a different message. Start a new send operation."
            }
            existing
        }

        // Retry push after resolving an idempotent request: the original request
        // may have committed the row but lost its response before push was queued.
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

    suspend fun uploadChatAttachment(context: Context, conversationId: String, uri: Uri, mimeType: String, displayName: String): MessageRecord {
        val uid = userId()
        val safeName = displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(90).ifBlank { "attachment" }
        val objectPath = "$conversationId/$uid/${java.util.UUID.randomUUID()}_$safeName"
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read the selected attachment.")
        }
        require(bytes.isNotEmpty()) { "The selected attachment is empty." }
        require(bytes.size <= 25 * 1024 * 1024) { "Attachments must be 25 MB or smaller." }
        runRequest {
            val token = SupabaseAuthRepository.currentAccessToken() ?: error("Sign in again to upload attachments.")
            val encodedPath = objectPath.split("/").joinToString("/") { enc(it) }
            val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/chat-attachments/" + encodedPath).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", mimeType.ifBlank { "application/octet-stream" })
                setRequestProperty("x-upsert", "false")
            }
            try {
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("Attachment upload failed ($code): ${response.take(160)}")
            } finally { connection.disconnect() }
            ""
        }
        val payload = JSONObject()
            .put("conversation_id", conversationId)
            .put("sender_id", uid)
            .put("body", displayName.ifBlank { "Attachment" })
            .put("attachment_path", objectPath)
            .put("attachment_mime_type", mimeType.ifBlank { "application/octet-stream" })
            .put("attachment_name", safeName)
        val response = try {
            runRequest { request("POST", "messages", payload, "return=representation") }
        } catch (insertError: Exception) {
            // Roll back only on a definite client-side HTTP rejection. A timeout,
            // connection reset, or server error can occur after the row committed;
            // deleting the object then would leave a valid message with broken media.
            val rejectedByServer = insertError.message?.startsWith("Request failed (4") == true
            if (rejectedByServer) {
                val rollbackError = runCatching {
                    withContext(NonCancellable) { deleteUploadedChatAttachment(objectPath) }
                }.exceptionOrNull()
                if (rollbackError != null) {
                    insertError.addSuppressed(rollbackError)
                    if (insertError !is kotlinx.coroutines.CancellationException) {
                        throw IllegalStateException(
                            "Message was rejected and uploaded-file cleanup could not be confirmed. Refresh before retrying.",
                            insertError
                        )
                    }
                }
            }
            throw insertError
        }
        val row = JSONArray(response).getJSONObject(0)
        val saved = MessageRecord(
            row.getString("id"), row.getString("conversation_id"), row.getString("sender_id"),
            row.optString("body"), row.getString("created_at"), row.optString("attachment_path"),
            row.optString("attachment_mime_type"), row.optString("attachment_name")
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { request("POST", "functions/v1/send-message-push", JSONObject().put("conversation_id", saved.conversationId).put("message_id", saved.id)) }
        }
        return saved
    }

    /** Best-effort rollback using the same user's JWT and existing Storage DELETE policy. */
    private suspend fun deleteUploadedChatAttachment(objectPath: String) = runRequest {
        val token = SupabaseAuthRepository.currentAccessToken()
            ?: error("Sign in again to clean up the uploaded attachment.")
        val encodedPath = objectPath.split("/").joinToString("/") { enc(it) }
        val connection = (URL(
            BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/chat-attachments/" + encodedPath
        ).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Storage rollback failed (HTTP $code).")
            ""
        } finally {
            connection.disconnect()
        }
    }

    suspend fun createSignedAttachmentUrl(objectPath: String): String = runRequest {
        val token = SupabaseAuthRepository.currentAccessToken() ?: error("Sign in again to open attachments.")
        val encodedPath = objectPath.split("/").joinToString("/") { enc(it) }
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/sign/chat-attachments/" + encodedPath).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(JSONObject().put("expiresIn", 3600).toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Could not create attachment URL ($code).")
            val signed = JSONObject(response).optString("signedURL")
            require(signed.isNotBlank()) { "Storage returned an empty signed URL." }
            if (signed.startsWith("https://")) signed else {
                require(signed.startsWith("/")) { "Storage returned an invalid signed URL." }
                BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1" + signed
            }
        } finally { connection.disconnect() }
    }

    /**
     * Downloads to app-private cache and atomically publishes only a complete, non-empty file.
     * Recipient confirmation is retried only after the atomic move succeeds; the sender is excluded.
     */
    suspend fun downloadAttachmentToCache(
        context: Context,
        messageId: String,
        senderId: String,
        objectPath: String,
        mimeType: String?,
        displayName: String?
    ): Uri = withContext(Dispatchers.IO) {
        val currentUserId = userId()
        require(messageId.isNotBlank()) { "Missing attachment message ID." }
        require(objectPath.isNotBlank() && !objectPath.startsWith("/") &&
            objectPath.split("/").none { it == "." || it == ".." }) { "Invalid attachment path." }

        val attachmentDir = File(context.cacheDir, "attachments").apply {
            if (!exists() && !mkdirs()) error("Could not prepare the private attachment cache.")
            if (!isDirectory) error("Attachment cache path is not a directory.")
        }
        val extension = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "audio/ogg" -> "ogg"
            "application/pdf" -> "pdf"
            else -> displayName?.substringAfterLast('.', "")?.replace(Regex("[^A-Za-z0-9]"), "")
                ?.take(8)?.takeIf { it.isNotBlank() } ?: "bin"
        }
        val finalFile = File(attachmentDir, "${messageId}.$extension")
        val tempFile = File.createTempFile("${messageId}_", ".part", attachmentDir)
        try {
            val signedUrl = createSignedAttachmentUrl(objectPath)
            val connection = (URL(signedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Attachment download failed (HTTP $code).")
                val expectedLength = connection.contentLengthLong
                connection.inputStream.use { input ->
                    tempFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count
                            if (total > 25L * 1024 * 1024) error("Attachment exceeds the 25 MB download limit.")
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
                if (!tempFile.isFile || tempFile.length() <= 0L) error("Downloaded attachment is empty.")
                if (expectedLength >= 0L && tempFile.length() != expectedLength) {
                    error("Attachment download was incomplete; please retry.")
                }
            } finally {
                connection.disconnect()
            }

            // Same-directory move ensures an atomic publication on the cache filesystem.
            Files.move(tempFile.toPath(), finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

            // This is intentionally after successful HTTP, length, non-empty, and atomic-move checks.
            if (senderId != currentUserId) {
                var lastError: Exception? = null
                for (attempt in 0 until 3) {
                    try {
                        request("POST", "functions/v1/confirm-message-media-download",
                            JSONObject().put("message_id", messageId))
                        lastError = null
                        break
                    } catch (error: Exception) {
                        lastError = error
                        if (attempt < 2) kotlinx.coroutines.delay(300L * (attempt + 1))
                    }
                }
                if (lastError != null) {
                    error("Attachment is cached, but recipient confirmation failed after retries. Tap OPEN FILE to retry.")
                }
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", finalFile)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
    /** Server-authorized read-only admin overview; never trusts client-supplied roles. */
    suspend fun loadAdminDashboardOverview(): AdminDashboardOverview {
        val root = JSONObject(runRequest {
            request("POST", "functions/v1/admin-console", JSONObject().put("action", "overview"))
        })
        val overview = root.getJSONObject("overview")
        return AdminDashboardOverview(
            role = root.optString("role", "read_only"),
            generatedAt = root.optString("generated_at"),
            registeredProfiles = overview.optInt("registered_profiles"),
            conversations = overview.optInt("conversations"),
            messages = overview.optInt("messages"),
            activeCalls = overview.optInt("active_calls"),
            activeUsersLast90Seconds = overview.optInt("active_users_last_90_seconds")
        )
    }

    /** Audit rows are returned only after the Edge Function validates admin membership. */
    suspend fun loadAdminAuditLog(): List<AdminAuditEvent> {
        val root = JSONObject(runRequest {
            request("POST", "functions/v1/admin-console", JSONObject().put("action", "audit_log"))
        })
        val rows = root.optJSONArray("events") ?: JSONArray()
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            AdminAuditEvent(
                id = row.optString("id"),
                actorId = row.optString("actor_id"),
                action = row.optString("action"),
                targetType = row.optString("target_type").takeIf { it.isNotBlank() && it != "null" },
                targetId = row.optString("target_id").takeIf { it.isNotBlank() && it != "null" },
                details = row.optJSONObject("details")?.toString().orEmpty(),
                createdAt = row.optString("created_at")
            )
        }
    }

    fun subscribeToMessages(conversationId: String, onChange: () -> Unit): SupabaseRealtime.Subscription? =
        SupabaseRealtime.subscribe("messages", "conversation_id=eq.$conversationId", { onChange() })

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

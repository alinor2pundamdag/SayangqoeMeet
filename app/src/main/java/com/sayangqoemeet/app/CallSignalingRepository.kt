package com.sayangqoemeet.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class CallSession(
    val id: String,
    val callerId: String,
    val calleeId: String,
    val type: String,
    val status: String,
    val offerSdp: String?,
    val answerSdp: String?
)

internal data class CallIceCandidate(
    val id: Long,
    val senderId: String,
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
)

/**
 * Authenticated PostgREST signaling adapter. It uses the active user JWT and
 * publishable key only; no service-role credential is ever shipped to Android.
 */
internal object CallSignalingRepository {
    private fun currentUserId() = SupabaseAuthRepository.currentUserId()
        ?: error("Sign in again before placing a call.")

    private fun request(method: String, path: String, body: JSONObject? = null, prefer: String? = null): String {
        val token = SupabaseAuthRepository.currentAccessToken()
            ?: error("Your session expired. Sign in again.")
        val connection = (URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$path")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 15000
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
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(response).optString("message") }.getOrNull()
                error("Call signaling failed ($code): ${message?.take(160) ?: "request rejected"}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun io(block: () -> String) = withContext(Dispatchers.IO) { block() }
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun parse(row: JSONObject) = CallSession(
        id = row.getString("id"),
        callerId = row.getString("caller_id"),
        calleeId = row.getString("callee_id"),
        type = row.getString("call_type"),
        status = row.getString("status"),
        offerSdp = row.optString("offer_sdp").takeIf { it.isNotBlank() && it != "null" },
        answerSdp = row.optString("answer_sdp").takeIf { it.isNotBlank() && it != "null" }
    )

    suspend fun createCall(targetUserId: String, type: String): CallSession {
        require(targetUserId.isNotBlank() && targetUserId != currentUserId()) { "Choose another registered user." }
        require(type == "voice" || type == "video")
        val body = JSONObject()
            .put("caller_id", currentUserId())
            .put("callee_id", targetUserId)
            .put("call_type", type)
            .put("status", "ringing")
        val rows = JSONArray(io { request("POST", "call_sessions?select=*", body, "return=representation") })
        return parse(rows.getJSONObject(0))
    }

    suspend fun pollIncoming(): List<CallSession> {
        val rows = JSONArray(io {
            request("GET", "call_sessions?callee_id=eq.${enc(currentUserId())}&status=eq.ringing&select=*&order=created_at.desc&limit=10")
        })
        return (0 until rows.length()).map { parse(rows.getJSONObject(it)) }
    }

    suspend fun getCall(id: String): CallSession {
        val rows = JSONArray(io {
            request("GET", "call_sessions?id=eq.${enc(id)}&select=*&limit=1")
        })
        check(rows.length() == 1) { "Call no longer exists or access was denied." }
        return parse(rows.getJSONObject(0))
    }

    suspend fun setOffer(id: String, sdp: String) {
        update(id, JSONObject().put("offer_sdp", sdp).put("status", "connecting"))
    }

    suspend fun setAnswer(id: String, sdp: String) {
        update(id, JSONObject().put("answer_sdp", sdp).put("status", "accepted").put("answered_at", java.time.Instant.now().toString()))
    }

    suspend fun setStatus(id: String, status: String) {
        require(status in setOf("accepted", "declined", "connecting", "connected", "ended", "missed", "failed"))
        val body = JSONObject().put("status", status)
        if (status in setOf("ended", "declined", "missed", "failed")) body.put("ended_at", java.time.Instant.now().toString())
        update(id, body)
    }

    private suspend fun update(id: String, body: JSONObject) {
        io { request("PATCH", "call_sessions?id=eq.${enc(id)}", body, "return=minimal") }
    }

    suspend fun addIce(id: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val body = JSONObject()
            .put("call_id", id)
            .put("sender_id", currentUserId())
            .put("candidate", candidate)
            .put("sdp_mid", sdpMid ?: JSONObject.NULL)
            .put("sdp_m_line_index", sdpMLineIndex)
        io { request("POST", "call_ice_candidates", body, "return=minimal") }
    }

    suspend fun loadIce(id: String, afterId: Long): List<CallIceCandidate> {
        val rows = JSONArray(io {
            request("GET", "call_ice_candidates?call_id=eq.${enc(id)}&id=gt.$afterId&select=id,sender_id,candidate,sdp_mid,sdp_m_line_index&order=id.asc&limit=100")
        })
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            CallIceCandidate(
                id = row.getLong("id"),
                senderId = row.getString("sender_id"),
                candidate = row.getString("candidate"),
                sdpMid = row.optString("sdp_mid").takeIf { it.isNotBlank() && it != "null" },
                sdpMLineIndex = row.optInt("sdp_m_line_index")
            )
        }
    }
}

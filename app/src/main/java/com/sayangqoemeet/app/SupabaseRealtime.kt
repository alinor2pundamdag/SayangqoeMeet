package com.sayangqoemeet.app

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Authenticated Supabase Realtime postgres_changes subscriber. */
internal object SupabaseRealtime {
    private const val TAG = "SupabaseRealtime"
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val refs = java.util.concurrent.atomic.AtomicLong(1)

    fun subscribe(table: String, filter: String, onRecord: (JSONObject) -> Unit, onFailure: (String) -> Unit = {}): Subscription? {
        val token = SupabaseAuthRepository.currentAccessToken() ?: run {
            onFailure("Sign in to receive live updates.")
            return null
        }
        val base = BuildConfig.SUPABASE_URL.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val url = "$base/realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_PUBLISHABLE_KEY}&vsn=1.0.0"
        val stopped = AtomicBoolean(false)
        val ref = refs.getAndIncrement().toString()
        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("postgres_changes", org.json.JSONArray().put(
                        JSONObject().put("event", "*").put("schema", "public").put("table", table).put("filter", filter)
                    ))
                webSocket.send(JSONObject().put("topic", "realtime:public:$table").put("event", "phx_join")
                    .put("payload", JSONObject().put("config", config).put("access_token", token))
                    .put("ref", ref).put("join_ref", ref).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (stopped.get()) return
                try {
                    val envelope = JSONObject(text)
                    if (envelope.optString("event") != "postgres_changes") return
                    val payload = envelope.optJSONObject("payload") ?: return
                    val data = payload.optJSONObject("data") ?: payload
                    val record = data.optJSONObject("record") ?: return
                    onRecord(record)
                } catch (error: Exception) {
                    Log.w(TAG, "Ignoring malformed Realtime event", error)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!stopped.get()) onFailure("Realtime connection interrupted.")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!stopped.get()) onFailure("Realtime connection closed.")
            }
        })
        return Subscription {
            if (stopped.compareAndSet(false, true)) {
                socket.close(1000, "subscription disposed")
                socket.cancel()
            }
        }
    }
    fun interface Subscription { fun close() }
}

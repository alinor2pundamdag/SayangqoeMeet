package com.sayangqoemeet.app

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Authenticated Supabase Realtime postgres_changes subscriber.
 *
 * A subscription owns its socket and reconnect loop. Transient network loss
 * reconnects with bounded exponential backoff; close() permanently disables
 * reconnects so disposed screens cannot leak sockets.
 */
internal data class RealtimeChange(
    val eventType: String,
    val record: JSONObject,
    val oldRecord: JSONObject
)

internal object SupabaseRealtime {
    private const val TAG = "SupabaseRealtime"
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val refs = AtomicLong(1)
    private val reconnectExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "supabase-realtime-reconnect").apply { isDaemon = true }
    }

    fun subscribe(
        table: String,
        filter: String,
        onRecord: (JSONObject) -> Unit,
        onFailure: (String) -> Unit = {}
    ): Subscription? = subscribeChanges(table, filter, { change ->
        val row = if (change.eventType == "DELETE") change.oldRecord else change.record
        onRecord(row)
    }, onFailure)

    fun subscribeChanges(
        table: String,
        filter: String,
        onChange: (RealtimeChange) -> Unit,
        onFailure: (String) -> Unit = {}
    ): Subscription? {
        if (SupabaseAuthRepository.currentAccessToken().isNullOrBlank()) {
            onFailure("Sign in to receive live updates.")
            return null
        }
        val subscription = ReconnectingSubscription(table, filter, onChange, onFailure)
        subscription.connect()
        return Subscription { subscription.close() }
    }

    private class ReconnectingSubscription(
        private val table: String,
        private val filter: String,
        private val onChange: (RealtimeChange) -> Unit,
        private val onFailure: (String) -> Unit
    ) {
        private val stopped = AtomicBoolean(false)
        private val reconnectScheduled = AtomicBoolean(false)
        private val attempt = AtomicInteger(0)
        private val socketRef = AtomicReference<WebSocket?>(null)

        fun connect() {
            if (stopped.get()) return
            val token = SupabaseAuthRepository.currentAccessToken()
            if (token.isNullOrBlank()) {
                onFailure("Sign in again to restore live updates.")
                scheduleReconnect()
                return
            }
            val base = BuildConfig.SUPABASE_URL.trimEnd('/')
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
            val url = "$base/realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_PUBLISHABLE_KEY}&vsn=1.0.0"
            val ref = refs.getAndIncrement().toString()
            val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (stopped.get()) {
                        webSocket.close(1000, "subscription disposed")
                        return
                    }
                    socketRef.set(webSocket)
                    attempt.set(0)
                    reconnectScheduled.set(false)
                    val postgresChange = JSONObject()
                        .put("event", "*")
                        .put("schema", "public")
                        .put("table", table)
                    if (filter.isNotBlank()) postgresChange.put("filter", filter)
                    val config = JSONObject()
                        .put("broadcast", JSONObject().put("ack", false).put("self", false))
                        .put("presence", JSONObject().put("key", ""))
                        .put("postgres_changes", org.json.JSONArray().put(postgresChange))
                    val joined = webSocket.send(
                        JSONObject()
                            .put("topic", "realtime:public:$table")
                            .put("event", "phx_join")
                            .put("payload", JSONObject().put("config", config).put("access_token", token))
                            .put("ref", ref)
                            .put("join_ref", ref)
                            .toString()
                    )
                    if (!joined) {
                        onFailure("Realtime subscription could not join; retrying.")
                        scheduleReconnect()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (stopped.get() || socketRef.get() !== webSocket) return
                    try {
                        val envelope = JSONObject(text)
                        val event = envelope.optString("event")
                        if (event == "phx_error" || event == "phx_close") {
                            onFailure("Realtime subscription was rejected or closed; retrying.")
                            scheduleReconnect()
                            return
                        }
                        if (event == "phx_reply") {
                            val payload = envelope.optJSONObject("payload") ?: return
                            if (payload.optString("status") == "error") {
                                onFailure("Realtime subscription was rejected; retrying.")
                                scheduleReconnect()
                            }
                            return
                        }
                        if (event != "postgres_changes") return
                        val payload = envelope.optJSONObject("payload") ?: return
                        val data = payload.optJSONObject("data") ?: payload
                        val type = data.optString("type").uppercase()
                        if (type !in setOf("INSERT", "UPDATE", "DELETE")) return
                        val record = data.optJSONObject("record") ?: JSONObject()
                        val oldRecord = data.optJSONObject("old_record") ?: JSONObject()
                        onChange(RealtimeChange(type, record, oldRecord))
                    } catch (error: Exception) {
                        Log.w(TAG, "Ignoring malformed Realtime event", error)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (stopped.get()) return
                    if (socketRef.compareAndSet(webSocket, null)) {
                        Log.w(TAG, "Realtime socket failed; scheduling reconnect", t)
                        onFailure("Realtime connection interrupted; reconnecting.")
                        scheduleReconnect()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (stopped.get()) return
                    if (socketRef.compareAndSet(webSocket, null) && code != 1000) {
                        onFailure("Realtime connection closed; reconnecting.")
                        scheduleReconnect()
                    }
                }
            })
            socketRef.set(socket)
        }

        private fun scheduleReconnect() {
            if (stopped.get() || !reconnectScheduled.compareAndSet(false, true)) return
            val retry = attempt.getAndIncrement().coerceAtMost(6)
            val delaySeconds = (1L shl retry).coerceAtMost(30L)
            reconnectExecutor.schedule({
                reconnectScheduled.set(false)
                if (!stopped.get()) connect()
            }, delaySeconds, TimeUnit.SECONDS)
        }

        fun close() {
            if (!stopped.compareAndSet(false, true)) return
            reconnectScheduled.set(false)
            socketRef.getAndSet(null)?.let { socket ->
                socket.close(1000, "subscription disposed")
                socket.cancel()
            }
        }
    }

    fun interface Subscription { fun close() }
}

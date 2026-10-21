package com.sayangqoemeet.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object FcmTokenRegistrar {
    fun registerCurrentToken(context: Context, scope: CoroutineScope) {
        // Call only after a valid Supabase session exists.
        if (FirebaseApp.getApps(context).isEmpty() ||
            SupabaseAuthRepository.currentUserId() == null
        ) return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) return@addOnSuccessListener
                scope.launch(Dispatchers.IO) {
                    runCatching { SupabaseDataRepository.saveDeviceToken(token) }
                }
            }
    }

    suspend fun unregisterCurrentToken(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val token = suspendCancellableCoroutine<String> { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
        if (token.isNotBlank() && SupabaseAuthRepository.currentUserId() != null) {
            SupabaseDataRepository.removeDeviceToken(token)
        }
    }
}

internal object MessageNotificationChannels {
    const val CHANNEL_ID = "sayangqoemeet_messages"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New SayangqoeMeet message alerts"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) channel.setAllowBubbles(true)
            manager.createNotificationChannel(channel)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.getNotificationChannel(CHANNEL_ID)?.let { existing ->
                existing.setAllowBubbles(true)
                manager.createNotificationChannel(existing)
            }
        }
    }
}

class SayangqoeMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Tokens can rotate before login. MainActivity retries the current token
        // after session restoration or successful authentication.
        if (SupabaseAuthRepository.currentUserId() == null) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { SupabaseDataRepository.saveDeviceToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Ringing is independent of notification permission; Android may suppress
        // the notification while the high-priority FCM data event still arrives.
        if (message.data["type"] == "incoming_call") {
            runCatching { CallForegroundService.start(this) }
            showIncomingCallNotification(message)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        MessageNotificationChannels.ensureCreated(this)
        val conversationId = message.data["conversation_id"]?.takeIf { it.matches(Regex("^[0-9a-fA-F-]{36}$")) }
        val senderName = message.data["sender_name"]?.takeIf { it.isNotBlank() } ?: "SayangqoeMeet"
        val bubbleIntent = if (conversationId != null) Intent(this, ChatBubbleActivity::class.java).apply {
            putExtra(ChatBubbleActivity.EXTRA_CONVERSATION_ID, conversationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } else Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_chats", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            conversationId?.hashCode() ?: 4101,
            bubbleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, MessageNotificationChannels.CHANNEL_ID)
        notificationBuilder.setSmallIcon(android.R.drawable.stat_notify_chat)
        notificationBuilder.setContentTitle(senderName)
        notificationBuilder.setContentText(message.data["message_preview"]?.takeIf { it.isNotBlank() } ?: "You have a new message.")
        notificationBuilder.setContentIntent(pendingIntent)
        notificationBuilder.setAutoCancel(true)
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (conversationId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val shortcutId = "conversation-$conversationId"
                val shortcutManager = getSystemService(ShortcutManager::class.java)
                val shortcutIntent = Intent(this, ChatBubbleActivity::class.java).apply {
                    putExtra(ChatBubbleActivity.EXTRA_CONVERSATION_ID, conversationId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val shortcutBuilder = ShortcutInfo.Builder(this, shortcutId)
                    .setShortLabel(senderName.take(25))
                    .setLongLabel("Chat with $senderName")
                    .setIcon(Icon.createWithResource(this, android.R.drawable.sym_action_chat))
                    .setIntent(shortcutIntent)
                    .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    shortcutBuilder.setLongLived(true)
                    shortcutBuilder.setPerson(Person.Builder().setName(senderName).build())
                }
                shortcutManager?.let { manager ->
                    val shortcut = shortcutBuilder.build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) manager.pushDynamicShortcut(shortcut)
                    else manager.addDynamicShortcuts(listOf(shortcut))
                }
                val bubble = NotificationCompat.BubbleMetadata.Builder(
                    pendingIntent,
                    IconCompat.createWithResource(this, android.R.drawable.sym_action_chat)
                ).setDesiredHeight((600 * resources.displayMetrics.density).toInt())
                    .setAutoExpandBubble(false)
                    .setSuppressNotification(false)
                    .build()
                notificationBuilder.setBubbleMetadata(bubble)
            }
        }
        val notification = notificationBuilder.build()
        runCatching {
            NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }
}

internal object CallNotificationChannels {
    const val CHANNEL_ID = "sayangqoemeet_calls"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming SayangqoeMeet voice and video calls"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
    }
}

private fun SayangqoeMessagingService.showIncomingCallNotification(message: RemoteMessage) {
    // Lint cannot infer the caller's permission guard across this helper boundary.
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val callId = message.data["call_id"]?.takeIf { it.isNotBlank() } ?: return
    val caller = message.data["caller_name"]?.takeIf { it.isNotBlank() } ?: "SayangqoeMeet user"
    val type = message.data["call_type"]?.takeIf { it == "video" } ?: "voice"
    CallNotificationChannels.ensureCreated(this)

    val acceptIntent = Intent(this, MainActivity::class.java).apply {
        action = "com.sayangqoemeet.app.OPEN_INCOMING_CALL"
        putExtra("open_calls", true)
        putExtra("incoming_call_id", callId)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val acceptPending = PendingIntent.getActivity(
        this, callId.hashCode(), acceptIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
        action = CallActionReceiver.ACTION_DECLINE
        putExtra("call_id", callId)
    }
    val declinePending = PendingIntent.getBroadcast(
        this, callId.hashCode() xor 0x51A, declineIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val callNotificationBuilder: NotificationCompat.Builder =
        NotificationCompat.Builder(this, CallNotificationChannels.CHANNEL_ID)
    callNotificationBuilder.setSmallIcon(android.R.drawable.sym_call_incoming)
    callNotificationBuilder.setContentTitle("Incoming $type call")
    callNotificationBuilder.setContentText(caller)
    callNotificationBuilder.setCategory(NotificationCompat.CATEGORY_CALL)
    callNotificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX)
    callNotificationBuilder.setContentIntent(acceptPending)
    callNotificationBuilder.setOngoing(true)
    callNotificationBuilder.setAutoCancel(false)
    callNotificationBuilder.addAction(android.R.drawable.sym_action_call, "Accept", acceptPending)
    callNotificationBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePending)
    val notification = callNotificationBuilder.build()
    runCatching { NotificationManagerCompat.from(this).notify(callId.hashCode(), notification) }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra("call_id") ?: return
        if (intent.action != ACTION_DECLINE) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { CallSignalingRepository.setStatus(callId, "declined") }
                NotificationManagerCompat.from(context).cancel(callId.hashCode())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.sayangqoemeet.app.DECLINE_CALL"
    }
}

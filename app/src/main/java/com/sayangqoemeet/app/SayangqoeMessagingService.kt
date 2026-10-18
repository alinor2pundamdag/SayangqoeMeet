package com.sayangqoemeet.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
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

internal object FcmTokenRegistrar {
    fun registerCurrentToken(context: Context, scope: CoroutineScope) {
        // Firebase is deliberately optional until the project-specific app config is supplied.
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            scope.launch(Dispatchers.IO) {
                runCatching { SupabaseDataRepository.saveDeviceToken(token) }
            }
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
            manager.createNotificationChannel(channel)
        }
    }
}

class SayangqoeMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { SupabaseDataRepository.saveDeviceToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        if (message.data["type"] == "incoming_call") {
            showIncomingCallNotification(message)
            return
        }

        MessageNotificationChannels.ensureCreated(this)
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            4101,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MessageNotificationChannels.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("SayangqoeMeet")
            .setContentText("You have a new message.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
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
    val notification = NotificationCompat.Builder(this, CallNotificationChannels.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.sym_call_incoming)
        .setContentTitle("Incoming $type call")
        .setContentText(caller)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setFullScreenIntent(acceptPending, false)
        .setContentIntent(acceptPending)
        .setOngoing(true)
        .setAutoCancel(false)
        .addAction(android.R.drawable.sym_action_call, "Accept", acceptPending)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePending)
        .build()
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

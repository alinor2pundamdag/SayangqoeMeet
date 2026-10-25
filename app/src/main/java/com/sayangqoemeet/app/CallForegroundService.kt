package com.sayangqoemeet.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground ringtone/vibration while an incoming call awaits a response. */
internal class CallForegroundService : Service() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var activeCallId: String? = null
    private var foregroundNotificationId: Int = NOTIFICATION_ID
    private var callerName: String = "SayangqoeMeet user"
    private var callType: String = "voice"
    private var activeCallMode: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusPollJob: Job? = null
    private var timeoutRunnable: Runnable? = null

    override fun onCreate() { super.onCreate(); createChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            intent.getStringExtra(EXTRA_CALL_ID)?.let(::cancelCallNotification)
            stopAlertAndSelf()
            return START_NOT_STICKY
        }
        activeCallId = intent?.getStringExtra(EXTRA_CALL_ID)?.takeIf { PushPayloadValidation.isUuid(it) }
        if (activeCallId == null) {
            stopAlertAndSelf()
            return START_NOT_STICKY
        }
        callerName = intent?.getStringExtra(EXTRA_CALLER_NAME)?.takeIf { it.isNotBlank() } ?: "SayangqoeMeet user"
        callType = intent?.getStringExtra(EXTRA_CALL_TYPE)?.takeIf { it == "video" } ?: "voice"
        foregroundNotificationId = activeCallId.hashCode()
        activeCallMode = intent?.action == ACTION_START_ACTIVE_CALL
        if (activeCallMode) {
            timeoutRunnable?.let(handler::removeCallbacks)
            timeoutRunnable = null
            stopRinging()
            startForegroundWithType(buildActiveCallNotification(activeCallId!!), activeCallTypeFlags())
            startStatusPolling()
        } else {
            startForegroundWithType(buildIncomingCallNotification(activeCallId!!), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            startRinging()
            scheduleBoundedStop()
            startStatusPolling()
        }
        return START_NOT_STICKY
    }

    private fun scheduleBoundedStop() {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = Runnable {
            val callId = activeCallId
            if (callId.isNullOrBlank()) {
                stopAlertAndSelf()
            } else {
                serviceScope.launch {
                    try {
                        val call = CallSignalingRepository.getCall(callId)
                        // A bounded local ringtone must also close the server-side
                        // unanswered call, otherwise the caller can ring forever.
                        if (
                            call.status == "ringing" &&
                            call.calleeId == SupabaseAuthRepository.currentUserId()
                        ) {
                            CallSignalingRepository.setStatus(callId, "missed")
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The local alert still stops on time if the device is offline.
                        // Server state remains visible for later reconciliation.
                    } finally {
                        stopAlertAndSelf()
                    }
                }
            }
        }.also {
            handler.postDelayed(it, MAX_RINGING_MILLIS)
        }
    }

    private fun startStatusPolling() {
        statusPollJob?.cancel()
        val callId = activeCallId ?: return
        statusPollJob = serviceScope.launch {
            while (isActive) {
                delay(STATUS_POLL_MILLIS)
                val call = try {
                    CallSignalingRepository.getCall(callId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null // Keep ringing only until the hard timeout if temporarily offline.
                }
                if (call != null) {
                    val terminal = call.status in setOf("ended", "declined", "cancelled", "missed", "failed")
                    if ((activeCallMode && terminal) || (!activeCallMode && call.status != "ringing")) {
                        stopAlertAndSelf()
                        return@launch
                    }
                }
            }
        }
    }

    private fun activeCallTypeFlags(): Int =
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
            (if (callType == "video") ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)

    private fun startForegroundWithType(notification: Notification, serviceTypes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, foregroundNotificationId, notification, serviceTypes)
        } else {
            startForeground(foregroundNotificationId, notification)
        }
    }

    private fun buildActiveCallNotification(callId: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.sayangqoemeet.app.OPEN_ACTIVE_CALL"
            putExtra("open_calls", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = android.app.PendingIntent.getActivity(
            this, callId.hashCode(), openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(if (callType == "video") "SayangqoeMeet video call" else "SayangqoeMeet voice call")
            .setContentText("Call in progress · Tap to return")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun buildIncomingCallNotification(callId: String): Notification {
        val acceptIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.sayangqoemeet.app.OPEN_INCOMING_CALL"
            putExtra("open_calls", true)
            putExtra("incoming_call_id", callId)
            putExtra("call_type", callType)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val acceptPending = android.app.PendingIntent.getActivity(
            this, callId.hashCode(), acceptIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePending = android.app.PendingIntent.getBroadcast(
            this, callId.hashCode() xor 0x51A, declineIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming $callType call")
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(acceptPending)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.sym_action_call, "Accept", acceptPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePending)
            .build()
    }

    private fun cancelCallNotification(callId: String) {
        runCatching { NotificationManagerCompat.from(this).cancel(callId.hashCode()) }
    }

    private fun stopAlertAndSelf() {
        statusPollJob?.cancel()
        statusPollJob = null
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        activeCallId?.let(::cancelCallNotification)
        stopRinging()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Ringtone and vibration for incoming calls"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    private fun startRinging() {
        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also {
                it.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                it.play()
            }
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        }
        val pattern = longArrayOf(0, 700, 500, 700, 1000)
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else { @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0) }
        }
    }

    private fun stopRinging() {
        ringtone?.let { runCatching { it.stop() } }; ringtone = null
        vibrator?.cancel(); vibrator = null
    }

    override fun onDestroy() {
        statusPollJob?.cancel()
        timeoutRunnable?.let(handler::removeCallbacks)
        stopRinging()
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.sayangqoemeet.app.action.START_CALL_RING"
        const val ACTION_STOP = "com.sayangqoemeet.app.action.STOP_CALL_RING"
        private const val ACTION_START_ACTIVE_CALL = "com.sayangqoemeet.app.action.START_ACTIVE_CALL"
        private const val EXTRA_CALL_ID = "call_id"
        private const val EXTRA_CALLER_NAME = "caller_name"
        private const val EXTRA_CALL_TYPE = "call_type"
        private const val CHANNEL_ID = "incoming_calls"
        private const val NOTIFICATION_ID = 4207
        private const val MAX_RINGING_MILLIS = 60_000L
        private const val STATUS_POLL_MILLIS = 5_000L

        fun start(
            context: Context,
            callId: String?,
            callerName: String? = null,
            callType: String? = null
        ) {
            if (callId == null || !PushPayloadValidation.isUuid(callId)) return
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, CallForegroundService::class.java).setAction(ACTION_START)
                    .putExtra(EXTRA_CALL_ID, callId)
                    .putExtra(EXTRA_CALLER_NAME, callerName)
                    .putExtra(EXTRA_CALL_TYPE, callType)
            )
        }

        fun startActiveCall(context: Context, callId: String, callType: String) {
            require(PushPayloadValidation.isUuid(callId)) { "Invalid call ID." }
            require(callType == "voice" || callType == "video") { "Invalid call type." }
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, CallForegroundService::class.java).setAction(ACTION_START_ACTIVE_CALL)
                    .putExtra(EXTRA_CALL_ID, callId)
                    .putExtra(EXTRA_CALL_TYPE, callType)
            )
        }

        fun stop(context: Context, callId: String? = null) {
            context.startService(Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP)
                .putExtra(EXTRA_CALL_ID, callId))
        }
    }
}

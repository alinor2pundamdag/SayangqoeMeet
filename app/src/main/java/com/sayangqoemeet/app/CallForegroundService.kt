package com.sayangqoemeet.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
        activeCallId = intent?.getStringExtra(EXTRA_CALL_ID)?.takeIf { it.isNotBlank() }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming SayangqoeMeet call")
            .setContentText("Open SayangqoeMeet to answer or decline.")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).build()
        startForeground(NOTIFICATION_ID, notification)
        startRinging()
        scheduleBoundedStop()
        startStatusPolling()
        return START_NOT_STICKY
    }

    private fun scheduleBoundedStop() {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = Runnable { stopAlertAndSelf() }.also {
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
                if (call != null && call.status != "ringing") {
                    stopAlertAndSelf()
                    return@launch
                }
            }
        }
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
        private const val EXTRA_CALL_ID = "call_id"
        private const val CHANNEL_ID = "incoming_calls"
        private const val NOTIFICATION_ID = 4207
        private const val MAX_RINGING_MILLIS = 60_000L
        private const val STATUS_POLL_MILLIS = 5_000L

        fun start(context: Context, callId: String? = null) = androidx.core.content.ContextCompat.startForegroundService(
            context, Intent(context, CallForegroundService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_CALL_ID, callId))

        fun stop(context: Context, callId: String? = null) {
            context.startService(Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP)
                .putExtra(EXTRA_CALL_ID, callId))
        }
    }
}

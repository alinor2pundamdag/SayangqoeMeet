package com.sayangqoemeet.app

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Call-scoped in-process router for explicit PiP commands. */
internal object CallPipController {
    const val ACTION_SET_MUTED = "com.sayangqoemeet.app.pip.SET_MUTED"
    const val ACTION_SET_CAMERA_ENABLED = "com.sayangqoemeet.app.pip.SET_CAMERA_ENABLED"
    const val ACTION_HANG_UP = "com.sayangqoemeet.app.pip.HANG_UP"
    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_ENABLED = "enabled"

    @Volatile private var activeCallId: String? = null
    @Volatile private var managerRef: WeakReference<WebRtcCallManager>? = null
    @Volatile private var stateRef: WeakReference<CallUiStateStore>? = null

    val isVideoCallActive: Boolean
        get() = stateRef?.get()?.state?.value?.let { it.callId != null && it.isVideoCall && !it.isTerminal } == true

    @Synchronized
    fun register(callId: String, manager: WebRtcCallManager, state: CallUiStateStore) {
        require(callId.isNotBlank())
        activeCallId = callId
        managerRef = WeakReference(manager)
        stateRef = WeakReference(state)
        state.begin(callId, manager.isVideoCall)
    }

    @Synchronized
    fun unregister(callId: String) {
        if (activeCallId != callId) return
        managerRef?.clear()
        stateRef?.clear()
        managerRef = null
        stateRef = null
        activeCallId = null
    }


    fun createActions(context: Context): List<RemoteAction> {
        val state = stateRef?.get()?.state?.value ?: return emptyList()
        val callId = state.callId ?: return emptyList()
        if (!state.isVideoCall || state.isTerminal) return emptyList()
        return listOf(
            createAction(context, 501, ACTION_SET_MUTED, callId,
                if (state.isMuted) "Unmute" else "Mute",
                if (state.isMuted) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off,
                !state.isMuted),
            createAction(context, 502, ACTION_SET_CAMERA_ENABLED, callId,
                if (state.isCameraEnabled) "Turn camera off" else "Turn camera on",
                android.R.drawable.presence_video_online, !state.isCameraEnabled),
            createHangUpAction(context, 503, callId)
        )
    }

    private fun createAction(
        context: Context, requestCode: Int, action: String, callId: String,
        title: String, iconRes: Int, enabled: Boolean
    ): RemoteAction {
        val intent = Intent(context, CallPipActionReceiver::class.java)
            .setAction(action).putExtra(EXTRA_CALL_ID, callId).putExtra(EXTRA_ENABLED, enabled)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(Icon.createWithResource(context, iconRes), title, title, pendingIntent)
    }

    private fun createHangUpAction(context: Context, requestCode: Int, callId: String): RemoteAction {
        val intent = Intent(context, CallPipActionReceiver::class.java)
            .setAction(ACTION_HANG_UP).putExtra(EXTRA_CALL_ID, callId)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
            "End call", "End call", pendingIntent
        )
    }

    fun setMuted(context: Context, callId: String, muted: Boolean) =
        dispatch(context, callId, ACTION_SET_MUTED, muted)

    fun setCameraEnabled(context: Context, callId: String, enabled: Boolean) =
        dispatch(context, callId, ACTION_SET_CAMERA_ENABLED, enabled)

    fun hangUp(context: Context, callId: String) {
        require(callId.isNotBlank())
        context.sendBroadcast(Intent(context, CallPipActionReceiver::class.java)
            .setAction(ACTION_HANG_UP).putExtra(EXTRA_CALL_ID, callId))
    }

    fun handle(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val manager: WebRtcCallManager
        val state: CallUiStateStore
        synchronized(this) {
            if (activeCallId != callId) return
            manager = managerRef?.get() ?: return
            state = stateRef?.get() ?: return
        }
        when (intent.action) {
            ACTION_SET_MUTED -> {
                val muted = intent.getBooleanExtra(EXTRA_ENABLED, false)
                state.setMuted(muted)
                manager.setMuted(muted)
            }
            ACTION_SET_CAMERA_ENABLED -> {
                if (!manager.isVideoCall) return
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
                state.setCameraEnabled(enabled)
                manager.setVideoEnabled(enabled)
            }
            ACTION_HANG_UP -> {
                state.setPhase(CallPhase.ENDING)
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    try {
                        manager.hangUp("ended")
                    } finally {
                        unregister(callId)
                        state.clear()
                    }
                }
            }
        }
    }

    private fun dispatch(context: Context, callId: String, action: String, enabled: Boolean) {
        require(callId.isNotBlank()) { "A call ID is required." }
        context.sendBroadcast(Intent(context, CallPipActionReceiver::class.java)
            .setAction(action).putExtra(EXTRA_CALL_ID, callId).putExtra(EXTRA_ENABLED, enabled))
    }
}

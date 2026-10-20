package com.sayangqoemeet.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.RemoteAction
import android.graphics.drawable.Icon
import java.lang.ref.WeakReference

/**
 * Routes PiP actions to the active call.
 *
 * The call screen registers its current manager while a call is active.
 * No credentials or signaling secrets are stored here.
 */
internal object CallPipController {

    const val ACTION_MUTE =
        "com.sayangqoemeet.app.pip.MUTE"

    const val ACTION_CAMERA =
        "com.sayangqoemeet.app.pip.CAMERA"

    const val ACTION_END_CALL =
        "com.sayangqoemeet.app.pip.END_CALL"

    private var managerRef: WeakReference<WebRtcCallManager>? = null

    @Volatile
    var isVideoCallActive: Boolean = false
        private set

    @Volatile
    var isMuted: Boolean = false
        private set

    @Volatile
    var isCameraEnabled: Boolean = true
        private set

    fun register(
        manager: WebRtcCallManager,
        isVideo: Boolean
    ) {
        managerRef = WeakReference(manager)
        isVideoCallActive = isVideo
        isMuted = false
        isCameraEnabled = isVideo
    }

    fun clear(manager: WebRtcCallManager? = null) {
        val current = managerRef?.get()

        if (manager == null || current === manager) {
            managerRef?.clear()
            managerRef = null
            isVideoCallActive = false
            isMuted = false
            isCameraEnabled = true
        }
    }

    fun toggleMute(): Boolean? {
        val manager = managerRef?.get() ?: return null

        isMuted = !isMuted
        manager.setMuted(isMuted)

        return isMuted
    }

    fun toggleCamera(): Boolean? {
        val manager = managerRef?.get() ?: return null

        if (!isVideoCallActive) return null

        isCameraEnabled = !isCameraEnabled
        manager.setVideoEnabled(isCameraEnabled)

        return isCameraEnabled
    }

    fun activeManager(): WebRtcCallManager? =
        managerRef?.get()

    fun createAction(
        context: Context,
        requestCode: Int,
        action: String,
        title: String,
        iconRes: Int
    ): RemoteAction {
        val intent = Intent(context, CallPipActionReceiver::class.java)
            .setAction(action)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return RemoteAction(
            Icon.createWithResource(context, iconRes),
            title,
            title,
            pendingIntent
        )
    }
}

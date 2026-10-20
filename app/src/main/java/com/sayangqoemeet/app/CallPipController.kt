package com.sayangqoemeet.app

import android.content.Context
import android.content.Intent

internal object CallPipController {
    const val ACTION_SET_MUTED =
        "com.sayangqoemeet.app.pip.SET_MUTED"

    const val ACTION_SET_CAMERA_ENABLED =
        "com.sayangqoemeet.app.pip.SET_CAMERA_ENABLED"

    const val ACTION_HANG_UP =
        "com.sayangqoemeet.app.pip.HANG_UP"

    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_ENABLED = "enabled"

    fun setMuted(
        context: Context,
        callId: String,
        muted: Boolean
    ) {
        dispatch(
            context = context,
            callId = callId,
            action = ACTION_SET_MUTED,
            enabled = muted
        )
    }

    fun setCameraEnabled(
        context: Context,
        callId: String,
        enabled: Boolean
    ) {
        dispatch(
            context = context,
            callId = callId,
            action = ACTION_SET_CAMERA_ENABLED,
            enabled = enabled
        )
    }

    fun hangUp(context: Context, callId: String) {
        val intent = Intent(context, CallPipActionReceiver::class.java)
            .setAction(ACTION_HANG_UP)
            .putExtra(EXTRA_CALL_ID, callId)

        context.sendBroadcast(intent)
    }

    private fun dispatch(
        context: Context,
        callId: String,
        action: String,
        enabled: Boolean
    ) {
        require(callId.isNotBlank()) { "A call ID is required." }

        val intent = Intent(context, CallPipActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_CALL_ID, callId)
            .putExtra(EXTRA_ENABLED, enabled)

        context.sendBroadcast(intent)
    }
}

package com.sayangqoemeet.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class CallPipActionReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {
            CallPipController.ACTION_MUTE -> {
                CallPipController.toggleMute()
            }

            CallPipController.ACTION_CAMERA -> {
                CallPipController.toggleCamera()
            }

            CallPipController.ACTION_END_CALL -> {
                val manager = CallPipController.activeManager()
                    ?: return

                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        manager.hangUp("ended")
                    } finally {
                        CallPipController.clear(manager)
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

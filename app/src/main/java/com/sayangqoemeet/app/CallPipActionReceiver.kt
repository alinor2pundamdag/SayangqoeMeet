package com.sayangqoemeet.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives explicit call-scoped PiP commands from the app's private receiver. */
internal class CallPipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CallPipController.handle(context.applicationContext, intent)
    }
}

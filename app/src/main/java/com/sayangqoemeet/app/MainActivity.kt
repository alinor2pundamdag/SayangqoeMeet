package com.sayangqoemeet.app

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sayangqoemeet.app.ui.theme.SayangqoeMeetTheme

class MainActivity : ComponentActivity() {

    var isInPiPMode by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SayangqoeMeetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    private fun enterCallPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!CallPipController.isVideoCallActive) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return

        val actions = buildList {
            add(
                CallPipController.createAction(
                    this@MainActivity,
                    501,
                    CallPipController.ACTION_MUTE,
                    if (CallPipController.isMuted) "Unmute" else "Mute",
                    if (CallPipController.isMuted)
                        android.R.drawable.ic_lock_silent_mode
                    else
                        android.R.drawable.ic_lock_silent_mode_off
                )
            )
            add(
                CallPipController.createAction(
                    this@MainActivity,
                    502,
                    CallPipController.ACTION_CAMERA,
                    if (CallPipController.isCameraEnabled) "Turn camera off" else "Turn camera on",
                    android.R.drawable.presence_video_online
                )
            )
            add(
                CallPipController.createAction(
                    this@MainActivity,
                    503,
                    CallPipController.ACTION_END_CALL,
                    "End call",
                    android.R.drawable.ic_menu_close_clear_cancel
                )
            )
        }

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)
            .build()

        enterPictureInPictureMode(params)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterCallPictureInPicture()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPiPMode = isInPictureInPictureMode
    }
}

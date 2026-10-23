package com.sayangqoemeet.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object { const val NOTIFICATION_PERMISSION_REQUEST = 1401 }

    private var isAuthReady by mutableStateOf(false)
    private var isAuthenticated by mutableStateOf(false)
    private var openChatsOnStart by mutableStateOf(false)
    private var incomingCallId by mutableStateOf<String?>(null)
    private var callRecipientId by mutableStateOf<String?>(null)
    private var callType by mutableStateOf("voice")
    private var notificationPermissionRequested = false
    private var authCallbackPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleAuthIntent(intent)
        handleNavigationIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        !isAuthReady -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }

                        isAuthenticated -> SignedInShell(
                            openChatsOnStart = openChatsOnStart,
                            initialCallId = incomingCallId,
                            initialCallRecipientId = callRecipientId,
                            initialCallType = callType,
                            onIncomingCallHandled = { handledId ->
                                if (incomingCallId == handledId) incomingCallId = null
                            },
                            onSignOut = {
                                // Serialize logout against onResume/session restoration so a
                                // late restore cannot put the signed-out account back on screen.
                                authCallbackPending = true
                                isAuthReady = false
                                lifecycleScope.launch {
                                    try {
                                        // Token deletion is best-effort; local logout must still
                                        // complete if the device is offline or the server is down.
                                        runCatching {
                                            FcmTokenRegistrar.unregisterCurrentToken(this@MainActivity)
                                        }
                                        var remoteSignOutFailure: Exception? = null
                                        try {
                                            SupabaseAuthRepository.signOut()
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            remoteSignOutFailure = error
                                        }
                                        isAuthenticated = false
                                        incomingCallId = null
                                        if (remoteSignOutFailure != null) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Signed out on this device. Server revocation could not be confirmed; reconnect to sync.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } finally {
                                        authCallbackPending = false
                                        isAuthReady = true
                                    }
                                }
                            }
                        )

                        else -> AuthGateScreen(
                            onAuthenticated = {
                                isAuthenticated = true
                                isAuthReady = true
                                FcmTokenRegistrar.registerCurrentToken(this@MainActivity, lifecycleScope)
                                requestNotificationPermissionIfNeeded()
                            }
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            // A deep-link callback owns session restoration when present. Do not
            // race it with a second startup restore that could render the auth gate.
            if (authCallbackPending) return@launch
            val restored = runCatching {
                SupabaseAuthRepository.restoreSession()
            }.getOrDefault(false)
            if (authCallbackPending) return@launch
            isAuthenticated = restored
            if (restored) {
                FcmTokenRegistrar.registerCurrentToken(this@MainActivity, lifecycleScope)
                requestNotificationPermissionIfNeeded()
            }
            isAuthReady = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Reconcile an existing session after returning from background, but do
        // not race startup or a deep-link callback's authoritative restore.
        if (!isAuthReady || authCallbackPending) return
        lifecycleScope.launch {
            val restoreResult = runCatching { SupabaseAuthRepository.restoreSession() }
            if (authCallbackPending) return@launch
            // A transient refresh/network failure is not proof that the user is
            // signed out. Preserve the current UI state unless the session check
            // completed successfully.
            restoreResult.onSuccess { restored ->
                isAuthenticated = restored
                if (restored) {
                    FcmTokenRegistrar.registerCurrentToken(this@MainActivity, lifecycleScope)
                    requestNotificationPermissionIfNeeded()
                }
            }
        }
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_chats", false) == true) {
            openChatsOnStart = true
        }
        if (intent?.getBooleanExtra("open_calls", false) == true) {
            incomingCallId = intent.getStringExtra("incoming_call_id")?.takeIf { it.isNotBlank() }
            callRecipientId = intent.getStringExtra("call_recipient_id")?.takeIf { it.isNotBlank() }
            callType = intent.getStringExtra("call_type")?.takeIf { it == "video" } ?: "voice"
        }
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (
            uri.scheme != "com.sayangqoemeet.app" ||
            uri.host != "login-callback"
        ) return

        authCallbackPending = true
        isAuthReady = false
        runCatching { SupabaseAuthRepository.handleDeepLink(intent) }
            .onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "Could not complete the authentication callback.",
                    Toast.LENGTH_LONG
                ).show()
            }

        lifecycleScope.launch {
            try {
                val restored = runCatching {
                    SupabaseAuthRepository.restoreSession()
                }.getOrDefault(false)
                isAuthenticated = restored
                if (restored) {
                    FcmTokenRegistrar.registerCurrentToken(this@MainActivity, lifecycleScope)
                    requestNotificationPermissionIfNeeded()
                }
            } finally {
                authCallbackPending = false
                isAuthReady = true
            }
        }
    }


    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationPermissionRequested) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermissionRequested = true
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            CallPipController.isVideoCallActive &&
            packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE
            )
        ) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()

            enterPictureInPictureMode(params)
        }
    }
}

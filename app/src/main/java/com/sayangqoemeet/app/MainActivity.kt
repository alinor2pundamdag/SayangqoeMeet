package com.sayangqoemeet.app

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
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isAuthReady by mutableStateOf(false)
    private var isAuthenticated by mutableStateOf(false)
    private var openChatsOnStart by mutableStateOf(false)
    private var incomingCallId by mutableStateOf<String?>(null)

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
                            onSignOut = {
                                lifecycleScope.launch {
                                    try {
                                        // Remove this installation's token while the old
                                        // session is still valid. If cleanup fails, keep the
                                        // session active rather than leaving a stale token.
                                        FcmTokenRegistrar.unregisterCurrentToken(this@MainActivity)
                                        SupabaseAuthRepository.signOut()
                                        isAuthenticated = false
                                        incomingCallId = null
                                    } catch (error: Exception) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            error.message ?: "Could not safely sign out. Please try again.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )

                        else -> AuthGateScreen(
                            onAuthenticated = {
                                isAuthenticated = true
                                isAuthReady = true
                                FcmTokenRegistrar.registerCurrentToken(this@MainActivity, lifecycleScope)
                            }
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            isAuthenticated = runCatching {
                SupabaseAuthRepository.restoreSession()
            }.getOrDefault(false)
            if (isAuthenticated) {
                FcmTokenRegistrar.registerCurrentToken(this@MainActivity, lifecycleScope)
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
        // OAuth confirmation may return to a new Activity instance depending on
        // browser/task behavior; refresh session whenever the app resumes.
        lifecycleScope.launch {
            val restored = runCatching { SupabaseAuthRepository.restoreSession() }.getOrDefault(false)
            isAuthenticated = restored
            isAuthReady = true
            if (restored) FcmTokenRegistrar.registerCurrentToken(this@MainActivity, lifecycleScope)
        }
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_chats", false) == true) {
            openChatsOnStart = true
        }
        if (intent?.getBooleanExtra("open_calls", false) == true) {
            incomingCallId = intent.getStringExtra("incoming_call_id")?.takeIf { it.isNotBlank() }
        }
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (
            uri.scheme != "com.sayangqoemeet.app" ||
            uri.host != "login-callback"
        ) return

        runCatching { SupabaseAuthRepository.handleDeepLink(intent) }
            .onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "Could not complete the authentication callback.",
                    Toast.LENGTH_LONG
                ).show()
            }

        lifecycleScope.launch {
            isAuthenticated = runCatching {
                SupabaseAuthRepository.restoreSession()
            }.getOrDefault(false)
            if (isAuthenticated) {
                FcmTokenRegistrar.registerCurrentToken(this@MainActivity, lifecycleScope)
            }
            isAuthReady = true
        }
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

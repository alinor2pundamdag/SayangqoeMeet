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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleAuthIntent(intent)

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
                            onSignOut = {
                                lifecycleScope.launch {
                                    runCatching { SupabaseAuthRepository.signOut() }
                                        .onSuccess { isAuthenticated = false }
                                        .onFailure { error ->
                                            Toast.makeText(
                                                this@MainActivity,
                                                error.message ?: "Could not sign out. Please try again.",
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
            isAuthReady = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
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

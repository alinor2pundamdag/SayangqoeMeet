package com.sayangqoemeet.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val AuthBackground = Color(0xFF070B18)
private val AuthPanel = Color(0xFF0B1222)
private val AuthText = Color(0xFFF0F5FF)
private val AuthCyan = Color(0xFF55F4FF)
private val AuthGreen = Color(0xFF39FF88)
private val AuthError = Color(0xFFFFC4A8)

@Composable
internal fun AuthGateScreen(onAuthenticated: () -> Unit) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Entirely local Compose animation: no network request, SDK, or telemetry.
    val rainbowTransition = rememberInfiniteTransition(label = "auth-neon-rainbow")
    val rainbowProgress by rainbowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200),
            repeatMode = RepeatMode.Restart
        ),
        label = "auth-neon-sweep"
    )
    val rainbowColors = listOf(
        Color(0xFF16CFFF),
        Color(0xFF39FF88),
        Color(0xFFFFFF45),
        Color(0xFFFF4FD8),
        Color(0xFFFF4A55),
        Color(0xFF16CFFF)
    )
    val rainbowBrush = Brush.linearGradient(
        colors = rainbowColors,
        start = Offset(x = rainbowProgress * 720f - 360f, y = 0f),
        end = Offset(x = rainbowProgress * 720f + 360f, y = 420f)
    )

    Surface(modifier = Modifier.fillMaxSize(), color = AuthBackground) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            // The staggered backing cards create a slot/deck effect while keeping
            // the actual controls in one stable, accessible layout.
            Box(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 8.dp, y = 9.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF14213A))
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 4.dp, y = 5.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF101B30))
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(AuthPanel)
                        .border(1.dp, Color(0xFF243650), RoundedCornerShape(28.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Animated neon border sweeps continuously left-to-right.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(rainbowBrush)
                            .padding(2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF080F20))
                                .padding(horizontal = 16.dp, vertical = 17.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "SayangqoeMeet",
                                style = MaterialTheme.typography.headlineMedium.copy(brush = rainbowBrush),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "PRIVATE MESSAGING · REAL-TIME CONNECTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9EB4D0)
                            )
                        }
                    }

                    Text(
                        text = if (isSignUp) "Create your account" else "Sign in to continue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AuthText
                    )

                    SlotInputSurface {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; status = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled = !isBusy,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuthCyan,
                                unfocusedBorderColor = Color(0xFF344761),
                                focusedLabelColor = AuthCyan,
                                cursorColor = AuthCyan,
                                focusedTextColor = AuthText,
                                unfocusedTextColor = AuthText
                            )
                        )
                    }

                    SlotInputSurface {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; status = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !isBusy,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuthCyan,
                                unfocusedBorderColor = Color(0xFF344761),
                                focusedLabelColor = AuthCyan,
                                cursorColor = AuthCyan,
                                focusedTextColor = AuthText,
                                unfocusedTextColor = AuthText
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val normalizedEmail = email.trim()
                            if (normalizedEmail.isBlank() || password.isBlank()) {
                                status = "Enter your email and password."
                                return@Button
                            }
                            isBusy = true
                            status = "Verifying credentials securely..."
                            scope.launch {
                                try {
                                    if (isSignUp) {
                                        status = SupabaseAuthRepository.signUp(normalizedEmail, password)
                                        if (SupabaseAuthRepository.restoreSession()) {
                                            onAuthenticated()
                                        }
                                    } else {
                                        SupabaseAuthRepository.signIn(normalizedEmail, password)
                                        onAuthenticated()
                                    }
                                } catch (error: Exception) {
                                    status = error.message
                                        ?: "Authentication failed. Check your connection and credentials."
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isBusy) 1.5.dp else 0.dp,
                                color = if (isBusy) AuthGreen else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        enabled = !isBusy,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBusy) Color(0xFF07351F) else Color(0xFF12364A),
                            contentColor = if (isBusy) AuthGreen else AuthCyan,
                            disabledContainerColor = Color(0xFF07351F),
                            disabledContentColor = AuthGreen
                        )
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(19.dp)
                                    .padding(end = 7.dp),
                                color = AuthGreen,
                                strokeWidth = 2.dp
                            )
                            Text("VERIFYING SECURELY…", fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                if (isSignUp) "CREATE ACCOUNT" else "SIGN IN",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            isSignUp = !isSignUp
                            status = null
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isSignUp) "Already have an account? Sign in"
                            else "New here? Create an account",
                            color = AuthCyan
                        )
                    }

                    TextButton(
                        onClick = {
                            val normalizedEmail = email.trim()
                            if (normalizedEmail.isBlank()) {
                                status = "Enter your email first to resend verification."
                                return@TextButton
                            }
                            isBusy = true
                            status = "Sending verification email securely..."
                            scope.launch {
                                try {
                                    status = SupabaseAuthRepository.resendSignupVerification(normalizedEmail)
                                } catch (error: Exception) {
                                    status = error.message ?: "Could not resend the verification email."
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RESEND SIGN-UP VERIFICATION", color = Color(0xFFB7C8DF))
                    }

                    status?.let { message ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = message,
                            color = if (isBusy) AuthGreen else AuthError,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotInputSurface(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF132139))
        )
        content()
    }
}

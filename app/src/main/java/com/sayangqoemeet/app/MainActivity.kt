package com.sayangqoemeet.app

import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Midnight = Color(0xFF070B18)
private val Panel = Color(0xFF10182B)
private val PanelRaised = Color(0xFF151F36)
private val NeonCyan = Color(0xFF55F4FF)
private val ElectricBlue = Color(0xFF5D8CFF)
private val Violet = Color(0xFFB58AFF)
private val TextPrimary = Color(0xFFF0F5FF)
private val TextMuted = Color(0xFF9BAAC5)
private val ErrorRed = Color(0xFFFF8297)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Midnight
                ) {
                    SayangqoeMeetRoot()
                }
            }
        }
    }
}

@Composable
private fun SayangqoeMeetRoot() {
    var signedIn by remember { mutableStateOf(false) }
    var checkingSession by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        signedIn = try {
            SupabaseAuthRepository.restoreSession()
        } catch (_: Exception) {
            false
        }
        checkingSession = false
    }

    if (checkingSession) {
        Box(Modifier.fillMaxSize().background(Midnight), contentAlignment = Alignment.Center) {
            Text("RESTORING SECURE SESSION…", color = NeonCyan, fontWeight = FontWeight.Bold)
        }
    } else if (signedIn) {
        SignedInShell(
            onSignOut = {
                scope.launch {
                    try {
                        SupabaseAuthRepository.signOut()
                    } finally {
                        signedIn = false
                    }
                }
            }
        )
    } else {
        SayangqoeMeetAuthScreen(onAuthenticated = { signedIn = true })
    }
}

@Composable
private fun SayangqoeMeetAuthScreen(onAuthenticated: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0C1530), Midnight, Color(0xFF090D1C))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 46.dp)
                .size(width = 250.dp, height = 180.dp)
                .shadow(80.dp, CircleShape, ambientColor = ElectricBlue, spotColor = NeonCyan)
                .background(Color(0x1A3C86FF), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(22.dp))
            BrandWordmark()
            Spacer(Modifier.height(10.dp))
            Text(
                text = "YOUR SPACE. YOUR PEOPLE.",
                color = TextMuted,
                fontSize = 11.sp,
                letterSpacing = 2.4.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .width(460.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(Color(0x6655F4FF), Color(0x334F6FFF), Color(0x44B58AFF))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .background(Panel.copy(alpha = 0.97f), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = if (isRegister) "Create your account" else "Welcome back",
                    color = TextPrimary,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = if (isRegister) "Start building your circle." else "Sign in to reconnect with your people.",
                    color = TextMuted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(26.dp))

                FormLabel("EMAIL")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        feedback = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("you@example.com", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(18.dp))

                FormLabel("PASSWORD")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        feedback = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("At least 8 characters", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(
                                if (showPassword) "HIDE" else "SHOW",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )

                AnimatedVisibility(visible = feedback != null) {
                    Text(
                        text = feedback.orEmpty(),
                        color = if (feedbackIsError) ErrorRed else NeonCyan,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val normalizedEmail = email.trim()
                        when {
                            !normalizedEmail.contains("@") || normalizedEmail.endsWith("@") ->
                                {
                                    feedbackIsError = true
                                    feedback = "Enter a valid email address."
                                }
                            password.length < 8 -> {
                                feedbackIsError = true
                                feedback = "Use a password with at least 8 characters."
                            }
                            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank() -> {
                                feedbackIsError = true
                                feedback = "Supabase key is missing. Configure SUPABASE_PUBLISHABLE_KEY in local.properties or the build environment."
                            }
                            else -> {
                                isLoading = true
                                feedback = null
                                coroutineScope.launch {
                                    try {
                                        feedback = if (isRegister) {
                                            SupabaseAuthRepository.signUp(normalizedEmail, password)
                                        } else {
                                            SupabaseAuthRepository.signIn(normalizedEmail, password)
                                        }
                                        feedbackIsError = false
                                        if (!isRegister) onAuthenticated()
                                    } catch (error: Exception) {
                                        feedbackIsError = true
                                        feedback = error.message?.take(180) ?: "Authentication failed. Please try again."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = ElectricBlue),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF286FE8),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isLoading) "PLEASE WAIT…" else if (isRegister) "CREATE ACCOUNT" else "SIGN IN",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRegister) "Already have an account?" else "New to SayangqoeMeet?",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = {
                            isRegister = !isRegister
                            feedback = null
                        }
                    ) {
                        Text(
                            text = if (isRegister) "Sign in" else "Create account",
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(Color(0xFF64E6B5), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "PRIVATE BY DESIGN  •  BUILT FOR CONNECTION",
                    color = TextMuted,
                    fontSize = 9.sp,
                    letterSpacing = 1.1.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BrandWordmark() {
    val letters = listOf(
        "S" to NeonCyan,
        "a" to Color(0xFF70EFFF),
        "y" to ElectricBlue,
        "a" to Color(0xFF6AA8FF),
        "n" to Color(0xFF75CFFF),
        "g" to Color(0xFF65F2E0),
        "q" to Color(0xFF72DFFF),
        "o" to ElectricBlue,
        "e" to Color(0xFF8BA0FF),
        "M" to Violet,
        "e" to Color(0xFF8F9EFF),
        "e" to Color(0xFF70DFFF),
        "t" to NeonCyan
    )
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        letters.forEach { (letter, color) ->
            Text(
                text = letter,
                color = color,
                fontSize = if (letter == "M") 31.sp else 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.1).sp,
                modifier = Modifier.shadow(12.dp, spotColor = color)
            )
        }
    }
}

@Composable
private fun FormLabel(label: String) {
    Text(
        text = label,
        color = Color(0xFFB7C6E2),
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = PanelRaised,
    unfocusedContainerColor = PanelRaised,
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = Color(0xFF35435F),
    cursorColor = NeonCyan,
    focusedLabelColor = NeonCyan,
    unfocusedLabelColor = TextMuted
)

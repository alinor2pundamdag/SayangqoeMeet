package com.sayangqoemeet.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun AuthGateScreen(onAuthenticated: () -> Unit) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF070B18)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "SayangqoeMeet",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF55F4FF)
                )
                Text(
                    text = if (isSignUp) "Create your account" else "Sign in to continue",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFF0F5FF)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; status = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isBusy
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; status = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !isBusy
                )

                Button(
                    onClick = {
                        val normalizedEmail = email.trim()
                        if (normalizedEmail.isBlank() || password.isBlank()) {
                            status = "Enter your email and password."
                            return@Button
                        }
                        isBusy = true
                        status = null
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
                                status = error.message ?: "Authentication failed. Check your connection and credentials."
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                ) {
                    if (isBusy) {
                        CircularProgressIndicator()
                    } else {
                        Text(if (isSignUp) "CREATE ACCOUNT" else "SIGN IN")
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
                        else "New here? Create an account"
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
                        status = null
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
                    Text("RESEND SIGN-UP VERIFICATION")
                }

                status?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = Color(0xFFFFC4A8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

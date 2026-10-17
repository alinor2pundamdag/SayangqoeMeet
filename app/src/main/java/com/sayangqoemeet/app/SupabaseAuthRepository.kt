package com.sayangqoemeet.app

import android.content.Intent
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email

/**
 * Supabase Auth wrapper. The app only uses a publishable/anon key; never place a
 * service-role or secret key in the Android application.
 */
object SupabaseAuthRepository {
    const val AUTH_REDIRECT_URL = "com.sayangqoemeet.app://login-callback"

    private val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                scheme = "com.sayangqoemeet.app"
                host = "login-callback"
            }
        }
    }

    fun handleDeepLink(intent: Intent) {
        client.handleDeeplinks(intent)
    }

    suspend fun signUp(email: String, password: String): String {
        client.auth.signUpWith(Email, redirectUrl = AUTH_REDIRECT_URL) {
            this.email = email
            this.password = password
        }
        return "Registration request sent. Check your email, then tap Confirm email address to return to SayangqoeMeet."
    }

    suspend fun resendSignupVerification(email: String): String {
        client.auth.resendEmail(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email = email.trim())
        return "Verification email request accepted. Check Inbox and Spam. If it does not arrive, the project email sender may be rate-limited or not delivering."
    }

    suspend fun signIn(email: String, password: String): String {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return "Signed in successfully."
    }

    suspend fun restoreSession(): Boolean {
        client.auth.awaitInitialization()
        return client.auth.currentSessionOrNull() != null
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun currentAccessToken(): String? = client.auth.currentSessionOrNull()?.accessToken

    suspend fun signOut() {
        client.auth.signOut()
    }
}

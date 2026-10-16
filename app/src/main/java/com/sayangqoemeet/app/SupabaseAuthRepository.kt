package com.sayangqoemeet.app

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

/**
 * Supabase Auth wrapper. The app only uses a publishable/anon key; never place a
 * service-role or secret key in the Android application.
 */
object SupabaseAuthRepository {
    private val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
        }
    }

    suspend fun signUp(email: String, password: String): String {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return "Registration request sent. Check your email if confirmation is enabled."
    }

    suspend fun signIn(email: String, password: String): String {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return "Signed in successfully."
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun currentAccessToken(): String? = client.auth.currentSessionOrNull()?.accessToken

    suspend fun signOut() {
        client.auth.signOut()
    }
}

package com.sayangqoemeet.app

/**
 * Validates UUIDs carried by untrusted notification intents and FCM data payloads.
 * Supabase remains authoritative for identity, membership, and call authorization.
 */
internal object PushPayloadValidation {
    private val uuidPattern = Regex(
        "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    )

    fun isUuid(value: String): Boolean = uuidPattern.matches(value)
}

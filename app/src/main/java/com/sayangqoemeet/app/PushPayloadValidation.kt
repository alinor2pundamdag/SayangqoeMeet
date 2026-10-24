package com.sayangqoemeet.app

/**
 * Validates UUIDs carried by untrusted notification intents and FCM data payloads.
 * Authorization still belongs to Supabase; this prevents malformed payloads from
 * triggering local navigation or foreground call UI.
 */
internal object PushPayloadValidation {
    private val uuidPattern = Regex(
        "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    )

    fun isUuid(value: String): Boolean = uuidPattern.matches(value)
}

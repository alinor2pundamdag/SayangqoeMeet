package com.sayangqoemeet.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CallSignalingRepositoryTest {
    @Test
    fun edgeFunctionRequestsUseFunctionsEndpointNotPostgrest() {
        assertEquals(
            "https://example.supabase.co/functions/v1/notify-incoming-call",
            supabaseRequestUrl("https://example.supabase.co/", "functions/v1/notify-incoming-call")
        )
    }

    @Test
    fun postgrestRequestsKeepRestV1Prefix() {
        assertEquals(
            "https://example.supabase.co/rest/v1/call_sessions?select=*",
            supabaseRequestUrl("https://example.supabase.co", "call_sessions?select=*")
        )
    }

    @Test
    fun leadingSlashDoesNotCreateDoubleSlash() {
        assertEquals(
            "https://example.supabase.co/functions/v1/get-turn-credentials",
            supabaseRequestUrl("https://example.supabase.co///", "/functions/v1/get-turn-credentials")
        )
    }
}

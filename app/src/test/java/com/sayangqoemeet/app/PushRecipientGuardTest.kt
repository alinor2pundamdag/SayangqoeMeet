package com.sayangqoemeet.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRecipientGuardTest {
    @Test
    fun acceptsPushForCurrentAccount() {
        assertTrue(shouldAcceptPushForUser("user-a", "user-a"))
    }

    @Test
    fun rejectsPushForDifferentAccount() {
        assertFalse(shouldAcceptPushForUser("user-b", "user-a"))
    }

    @Test
    fun rejectsMissingOrBlankIdentity() {
        assertFalse(shouldAcceptPushForUser(null, "user-a"))
        assertFalse(shouldAcceptPushForUser("", "user-a"))
        assertFalse(shouldAcceptPushForUser("user-a", null))
        assertFalse(shouldAcceptPushForUser("user-a", " "))
    }
}

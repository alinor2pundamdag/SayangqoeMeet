package com.sayangqoemeet.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPayloadValidationTest {
    @Test
    fun acceptsCanonicalUuidValues() {
        assertTrue(PushPayloadValidation.isUuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
        assertTrue(PushPayloadValidation.isUuid("ABCDEF12-3456-4ABC-9DEF-1234567890AB"))
    }

    @Test
    fun rejectsMalformedOrNonCanonicalIdentifiers() {
        assertFalse(PushPayloadValidation.isUuid(""))
        assertFalse(PushPayloadValidation.isUuid("                                  "))
        assertFalse(PushPayloadValidation.isUuid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        assertFalse(PushPayloadValidation.isUuid("not-a-uuid"))
        assertFalse(PushPayloadValidation.isUuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa"))
        assertFalse(PushPayloadValidation.isUuid("aaaaaaaa-aaaa-4aaa-caaa-aaaaaaaaaaaa"))
        assertFalse(PushPayloadValidation.isUuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/extra"))
    }
}

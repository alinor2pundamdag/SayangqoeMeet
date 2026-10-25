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
        listOf(
            "", "                                  ",
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "not-a-uuid", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa",
            "aaaaaaaa-aaaa-4aaa-caaa-aaaaaaaaaaaa",
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/extra"
        ).forEach { assertFalse("Expected rejection: $it", PushPayloadValidation.isUuid(it)) }
    }
}

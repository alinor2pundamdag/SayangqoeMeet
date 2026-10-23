package com.sayangqoemeet.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentAvailabilityTest {
    @Test
    fun missingPurgeTimestampKeepsAttachmentAvailable() {
        assertFalse(isAttachmentPurged(null))
        assertFalse(isAttachmentPurged(""))
        assertFalse(isAttachmentPurged("null"))
    }

    @Test
    fun confirmedPurgeTimestampMarksAttachmentUnavailable() {
        assertTrue(isAttachmentPurged("2026-10-23T10:00:00Z"))
    }
}

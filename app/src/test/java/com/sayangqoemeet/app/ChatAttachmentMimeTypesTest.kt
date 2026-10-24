package com.sayangqoemeet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAttachmentMimeTypesTest {
    @Test
    fun acceptsPlainTextForChatAttachments() {
        assertEquals("text/plain", supportedChatAttachmentMimeType("text/plain"))
        assertEquals("text/plain", supportedChatAttachmentMimeType(" text/plain; charset=UTF-8 "))
    }

    @Test
    fun normalizesSupportedMimeTypes() {
        assertEquals("image/jpeg", supportedChatAttachmentMimeType("IMAGE/JPEG"))
        assertEquals("audio/mp4", supportedChatAttachmentMimeType("audio/mp4"))
        assertEquals("video/quicktime", supportedChatAttachmentMimeType("video/quicktime"))
    }

    @Test
    fun rejectsMissingOrUnsupportedMimeTypesBeforeUpload() {
        assertNull(supportedChatAttachmentMimeType(null))
        assertNull(supportedChatAttachmentMimeType(""))
        assertNull(supportedChatAttachmentMimeType("application/octet-stream"))
        assertNull(supportedChatAttachmentMimeType("application/x-executable"))
        assertNull(supportedChatAttachmentMimeType("text/html"))
    }
}

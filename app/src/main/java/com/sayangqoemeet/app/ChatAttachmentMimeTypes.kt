package com.sayangqoemeet.app

/**
 * MIME types deliberately accepted by the private chat-attachments bucket.
 * Keep this list in sync with the forward storage-bucket migration.
 */
internal val supportedChatAttachmentMimeTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "audio/mp4",
    "audio/mpeg",
    "audio/ogg",
    "audio/webm",
    "audio/wav",
    "video/mp4",
    "video/webm",
    "video/quicktime",
    "text/plain"
)

internal fun supportedChatAttachmentMimeType(rawMimeType: String?): String? {
    val normalized = rawMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return normalized.takeIf { it in supportedChatAttachmentMimeTypes }
}

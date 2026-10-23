package com.sayangqoemeet.app

/** True only when the backend has confirmed that attachment bytes were purged. */
internal fun isAttachmentPurged(purgedAt: String?): Boolean =
    !purgedAt.isNullOrBlank() && purgedAt != "null"

package com.sayangqoemeet.app

/** Accept push data only when it is explicitly addressed to the active account. */
internal fun shouldAcceptPushForUser(currentUserId: String?, recipientUserId: String?): Boolean =
    !currentUserId.isNullOrBlank() &&
        !recipientUserId.isNullOrBlank() &&
        currentUserId == recipientUserId

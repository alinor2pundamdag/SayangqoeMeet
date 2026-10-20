package com.sayangqoemeet.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color

/** Dedicated Android notification-bubble surface for one authenticated conversation. */
class ChatBubbleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        setContent {
            MaterialTheme {
                Surface(color = Color(0xFF070B18)) {
                    if (conversationId.isNullOrBlank()) {
                        androidx.compose.material3.Text("Conversation unavailable.", color = Color.White)
                    } else {
                        ConversationScreen(
                            conversationId = conversationId,
                            onBack = { finish() },
                            onCall = { recipientId, callType ->
                                startActivity(Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("open_calls", true)
                                    putExtra("call_recipient_id", recipientId)
                                    putExtra("call_type", callType)
                                })
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}

package com.sayangqoemeet.app

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Inline player for an already-authorized private audio attachment. */
@Composable
internal fun InlineVoiceNotePlayer(
    message: MessageRecord,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    var cachedUri by remember(message.id) { mutableStateOf<Uri?>(null) }
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    var durationMs by remember(message.id) { mutableStateOf(0) }
    var positionMs by remember(message.id) { mutableStateOf(0) }
    var loading by remember(message.id) { mutableStateOf(false) }

    fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        isPlaying = false
        positionMs = 0
    }

    fun startPlayback(uri: Uri) {
        runCatching {
            val created = checkNotNull(MediaPlayer.create(context, uri)) {
                "This audio attachment could not be decoded on this device."
            }
            player = created
            durationMs = created.duration.coerceAtLeast(0)
            created.setOnCompletionListener {
                isPlaying = false
                positionMs = 0
                runCatching { it.seekTo(0) }
            }
            created.start()
            isPlaying = true
        }.onFailure { onError(it.message ?: "Could not play this voice note.") }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(100)
            val active = player
            if (active == null) {
                isPlaying = false
            } else {
                positionMs = runCatching { active.currentPosition }.getOrDefault(positionMs)
                if (runCatching { active.isPlaying }.getOrDefault(false).not()) isPlaying = false
            }
        }
    }

    DisposableEffect(message.id) {
        onDispose { releasePlayer() }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = !loading,
                onClick = {
                    val active = player
                    if (active != null) {
                        if (isPlaying) {
                            runCatching { active.pause() }
                            isPlaying = false
                        } else {
                            runCatching {
                                if (positionMs >= durationMs && durationMs > 0) active.seekTo(0)
                                active.start()
                                isPlaying = true
                            }.onFailure { onError(it.message ?: "Playback could not resume.") }
                        }
                    } else {
                        loading = true
                        scope.launch {
                            try {
                                val uri = SupabaseDataRepository.downloadAttachmentToCache(
                                    context = context,
                                    messageId = message.id,
                                    senderId = message.senderId,
                                    objectPath = message.attachmentPath ?: error("Audio attachment path is missing."),
                                    mimeType = message.attachmentMimeType,
                                    displayName = message.attachmentName ?: "voice-note"
                                )
                                cachedUri = uri
                                startPlayback(uri)
                            } catch (error: Exception) {
                                onError(error.message ?: "Could not download this voice note.")
                            } finally {
                                loading = false
                            }
                        }
                    }
                }
            ) {
                Text(
                    when {
                        loading -> "LOADING…"
                        isPlaying -> "PAUSE"
                        player != null -> "PLAY"
                        else -> "PLAY VOICE NOTE"
                    },
                    color = Color(0xFF55F4FF),
                    fontSize = 11.sp
                )
            }
            Text(formatAudioTime(positionMs), color = Color(0xFF9BAAC5), fontSize = 10.sp)
            Text(" / ", color = Color(0xFF9BAAC5), fontSize = 10.sp)
            Text(formatAudioTime(durationMs), color = Color(0xFF9BAAC5), fontSize = 10.sp)
        }
        Slider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onValueChange = { fraction ->
                val target = (fraction * durationMs).toInt().coerceIn(0, durationMs)
                positionMs = target
                runCatching { player?.seekTo(target) }
            },
            enabled = player != null && durationMs > 0
        )
    }
}

private fun formatAudioTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
    return "%02d:%02d".format(java.util.Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
}

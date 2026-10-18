package com.sayangqoemeet.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

private val CallBg = Color(0xFF070B18)
private val CallPanel = Color(0xFF121D32)
private val CallCyan = Color(0xFF55F4FF)
private val CallText = Color(0xFFF0F5FF)
private val CallMuted = Color(0xFF9BAAC5)
private val CallRed = Color(0xFFFF536A)

/** A real call entry point: session signaling and media are backed by Supabase and WebRTC. */
@Composable
internal fun CallCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetId by remember { mutableStateOf("") }
    var callType by remember { mutableStateOf("voice") }
    var incoming by remember { mutableStateOf<List<CallSession>>(emptyList()) }
    var activeCall by remember { mutableStateOf<CallSession?>(null) }
    var manager by remember { mutableStateOf<WebRtcCallManager?>(null) }
    var status by remember { mutableStateOf("Ready to call") }
    var error by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(true) }
    var videoOn by remember { mutableStateOf(true) }
    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.RECORD_AUDIO] == true &&
            (activeCall?.type != "video" || result[Manifest.permission.CAMERA] == true)
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke() else error = "Microphone permission is required; camera permission is required for video calls."
    }

    fun withPermissions(video: Boolean, action: () -> Unit) {
        val required = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (video && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.CAMERA)
            }
        }
        if (required.isEmpty()) action() else {
            pendingAction = action
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    fun startMedia(call: CallSession, caller: Boolean) {
        withPermissions(call.type == "video") {
            scope.launch {
                try {
                    error = null
                    activeCall = call
                    callType = call.type
                    val instance = WebRtcCallManager(
                        context.applicationContext, scope, call.id, call.type == "video",
                        onStatus = { status = it },
                        onError = { error = it }
                    )
                    manager = instance
                    instance.attachRenderers(localRenderer, remoteRenderer)
                    if (caller) instance.startAsCaller() else instance.acceptAsCallee()
                } catch (failure: Exception) {
                    error = failure.message ?: "Could not start the call."
                    status = "Call setup failed"
                }
            }
        }
    }

    LaunchedEffect(activeCall?.id) {
        while (activeCall == null) {
            runCatching { incoming = CallSignalingRepository.pollIncoming() }
                .onFailure { error = it.message ?: "Unable to check incoming calls." }
            delay(2500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val instance = manager
            if (instance != null) scope.launch { runCatching { instance.hangUp() } }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = CallBg) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CALLING", color = CallCyan, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("BACK", color = CallMuted) }
            }
            if (activeCall == null) {
                Text("Private voice and video calls", color = CallText)
                Text("Enter the recipient's registered Supabase user UUID.", color = CallMuted)
                OutlinedTextField(
                    value = targetId,
                    onValueChange = { targetId = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Recipient user UUID") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { callType = "voice" }, colors = ButtonDefaults.buttonColors(containerColor = if (callType == "voice") CallCyan else CallPanel)) { Text("VOICE", color = if (callType == "voice") CallBg else CallText) }
                    Button(onClick = { callType = "video" }, colors = ButtonDefaults.buttonColors(containerColor = if (callType == "video") CallCyan else CallPanel)) { Text("VIDEO", color = if (callType == "video") CallBg else CallText) }
                    Button(
                        enabled = targetId.isNotBlank(),
                        onClick = {
                            scope.launch {
                                try {
                                    val created = CallSignalingRepository.createCall(targetId, callType)
                                    startMedia(created, caller = true)
                                } catch (failure: Exception) {
                                    error = failure.message ?: "Could not create call."
                                }
                            }
                        }
                    ) { Text("CALL") }
                }
                Text("Incoming calls", color = CallCyan)
                if (incoming.isEmpty()) Text("No incoming calls.", color = CallMuted)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(incoming, key = { it.id }) { call ->
                        Card(colors = CardDefaults.cardColors(containerColor = CallPanel)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${call.type.uppercase()} CALL", color = CallText)
                                    Text("From ${call.callerId}", color = CallMuted)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { CallSignalingRepository.setStatus(call.id, "declined") }
                                        incoming = incoming.filterNot { it.id == call.id }
                                    }
                                }) { Text("DECLINE", color = CallRed) }
                                TextButton(onClick = { startMedia(call, caller = false) }) { Text("ACCEPT", color = CallCyan) }
                            }
                        }
                    }
                }
            } else {
                Text(status, color = CallCyan)
                if (activeCall?.type == "video") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AndroidView(
                            modifier = Modifier.weight(1f).height(210.dp),
                            factory = { view -> SurfaceViewRenderer(view).also { localRenderer = it; manager?.attachRenderers(it, remoteRenderer) } }
                        )
                        AndroidView(
                            modifier = Modifier.weight(1f).height(210.dp),
                            factory = { view -> SurfaceViewRenderer(view).also { remoteRenderer = it; manager?.attachRenderers(localRenderer, it) } }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { muted = !muted; manager?.setMuted(muted) }) { Text(if (muted) "UNMUTE" else "MUTE") }
                    Button(onClick = { speaker = !speaker; manager?.setSpeaker(speaker) }) { Text(if (speaker) "SPEAKER ON" else "SPEAKER OFF") }
                    Button(onClick = { videoOn = !videoOn; manager?.setVideoEnabled(videoOn) }, enabled = activeCall?.type == "video") { Text(if (videoOn) "CAMERA OFF" else "CAMERA ON") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { manager?.switchCamera() }, enabled = activeCall?.type == "video") { Text("FLIP CAMERA") }
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = CallRed),
                        onClick = {
                            val current = manager
                            scope.launch {
                                runCatching { current?.hangUp("ended") }
                                manager = null
                                activeCall = null
                                localRenderer = null
                                remoteRenderer = null
                                status = "Call ended"
                            }
                        }
                    ) { Text("HANG UP") }
                }
            }
            error?.let { Text(it, color = CallRed) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

package com.sayangqoemeet.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import org.webrtc.AudioTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URL
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Owns native WebRTC resources for one active call. Signaling is persisted through
 * authenticated Supabase PostgREST; media stays peer-to-peer. A TURN server is
 * still required for reliable connectivity on restrictive NATs.
 */
internal class WebRtcCallManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callId: String,
    private val videoEnabled: Boolean,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onCallEnded: (String) -> Unit = {}
) {
    val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioModule: JavaAudioDeviceModule? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: org.webrtc.VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var signalJob: Job? = null
    private var callStatusSubscription: SupabaseRealtime.Subscription? = null
    private val endNotified = AtomicBoolean(false)
    private var lastIceId = 0L
    private val disposed = AtomicBoolean(false)
    private var microphoneMuted = false
    private var speakerOn = true
    private var ringbackTone: ToneGenerator? = null

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localRenderer?.let { videoTrack?.removeSink(it) }
        remoteRenderer?.let { peer?.receivers?.forEach { receiver -> (receiver.track() as? VideoTrack)?.removeSink(it) } }
        localRenderer = local
        remoteRenderer = remote
        local?.let {
            it.init(eglBase.eglBaseContext, null)
            it.setMirror(true)
            videoTrack?.addSink(it)
        }
        remote?.let {
            it.init(eglBase.eglBaseContext, null)
            it.setMirror(false)
            peer?.receivers?.forEach { receiver -> (receiver.track() as? VideoTrack)?.addSink(it) }
        }
    }

    suspend fun startAsCaller() {
        initializePeer()
        val offer = createDescription(offer = true)
        CallSignalingRepository.setOffer(callId, offer.description)
        startRingbackTone()
        onStatus("Ringing — waiting for the other participant")
        startSignalPolling(caller = true)
    }

    suspend fun acceptAsCallee() {
        initializePeer()
        val call = CallSignalingRepository.getCall(callId)
        val offerSdp = call.offerSdp ?: error("The caller's offer has not arrived yet.")
        setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        val answer = createDescription(offer = false)
        CallSignalingRepository.setAnswer(callId, answer.description)
        onStatus("Connecting securely")
        startSignalPolling(caller = false)
    }

    private suspend fun initializePeer() = withContext(Dispatchers.Main) {
        check(!disposed.get()) { "Call has already been closed." }
        if (peer != null) return@withContext
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions()
        )
        audioModule = JavaAudioDeviceModule.builder(context.applicationContext).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        val iceServers = fetchIceServersOrFallback()
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                scope.launch {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            stopRingbackTone()
                            runCatching { CallSignalingRepository.setStatus(callId, "connected") }
                            onStatus("Connected")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> onStatus("Connection interrupted — reconnecting")
                        PeerConnection.IceConnectionState.FAILED -> {
                            onStatus("Connection failed")
                            onError("WebRTC could not establish a peer connection. A TURN server may be required.")
                            runCatching { CallSignalingRepository.setStatus(callId, "failed") }
                        }
                        else -> Unit
                    }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    runCatching {
                        CallSignalingRepository.addIce(
                            callId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex
                        )
                    }.onFailure { Log.w(TAG, "Could not upload ICE candidate", it) }
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream) {
                stream.videoTracks.forEach { track -> remoteRenderer?.let(track::addSink) }
            }
            override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out org.webrtc.MediaStream>) {
                (receiver.track() as? VideoTrack)?.let { track -> remoteRenderer?.let(track::addSink) }
            }
            override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
                (transceiver.receiver.track() as? VideoTrack)?.let { track -> remoteRenderer?.let(track::addSink) }
            }
        }) ?: error("Could not create WebRTC peer connection")

        val constraints = MediaConstraints()
        audioSource = factory?.createAudioSource(constraints)
        audioTrack = factory?.createAudioTrack("sayangqoe-audio", audioSource)
        audioTrack?.let { peer?.addTrack(it, listOf("sayangqoe-stream")) }

        if (videoEnabled) {
            val enumerator = Camera2Enumerator(context)
            val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
            if (cameraName == null) error("No compatible camera was found.")
            capturer = enumerator.createCapturer(cameraName, null)
                ?: error("Could not open the camera.")
            textureHelper = SurfaceTextureHelper.create("SayangqoeCapture", eglBase.eglBaseContext)
            videoSource = factory?.createVideoSource(capturer!!.isScreencast)
            capturer?.initialize(textureHelper, context, videoSource?.capturerObserver)
            capturer?.startCapture(640, 480, 24)
            videoTrack = factory?.createVideoTrack("sayangqoe-video", videoSource)
            videoTrack?.let { track ->
                peer?.addTrack(track, listOf("sayangqoe-stream"))
                localRenderer?.let(track::addSink)
            }
        }
        setSpeaker(true)
    }

    /**
     * Requests short-lived ICE credentials from the authenticated Supabase Edge Function.
     * Only the current user's access token and the public publishable key are sent.
     * If the function is unavailable, retain STUN-only connectivity rather than failing
     * call initialization; restrictive NATs may still require TURN and can fail to connect.
     */
    private suspend fun fetchIceServersOrFallback(): List<PeerConnection.IceServer> =
        withContext(Dispatchers.IO) {
            val fallback = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
            try {
                val token = SupabaseAuthRepository.currentAccessToken()
                    ?: error("Sign in again before starting a call.")
                val connection = (URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/get-turn-credentials")
                    .openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    check(code in 200..299) {
                        "get-turn-credentials returned HTTP $code"
                    }
                    val root = org.json.JSONObject(response)
                    val servers = root.optJSONArray("iceServers")
                        ?: error("get-turn-credentials response has no iceServers array")
                    val parsed = buildList {
                        for (index in 0 until servers.length()) {
                            val item = servers.optJSONObject(index) ?: continue
                            val urlsValue = item.opt("urls")
                            val urls = when (urlsValue) {
                                is String -> listOf(urlsValue)
                                is org.json.JSONArray -> (0 until urlsValue.length())
                                    .mapNotNull { urlsValue.optString(it).takeIf(String::isNotBlank) }
                                else -> emptyList()
                            }.filter { it.startsWith("stun:", ignoreCase = true) ||
                                it.startsWith("turn:", ignoreCase = true) ||
                                it.startsWith("turns:", ignoreCase = true) }
                            if (urls.isEmpty()) continue
                            val username = item.optString("username")
                            val credential = item.optString("credential")
                            val builder = PeerConnection.IceServer.builder(urls)
                            if (username.isNotBlank() && username != "null") builder.setUsername(username)
                            if (credential.isNotBlank() && credential != "null") builder.setPassword(credential)
                            add(builder.createIceServer())
                        }
                    }
                    check(parsed.isNotEmpty()) {
                        "get-turn-credentials returned no usable ICE servers"
                    }
                    parsed
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Dynamic ICE credentials unavailable; falling back to STUN", error)
                fallback
            }
        }

    private suspend fun createDescription(offer: Boolean): SessionDescription =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val observer = object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) {
                    peer?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription) = Unit
                        override fun onSetSuccess() {
                            if (continuation.isActive) continuation.resume(description)
                        }
                        override fun onCreateFailure(error: String) {
                            if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(error)))
                        }
                        override fun onSetFailure(error: String) {
                            if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(error)))
                        }
                    }, description)
                }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(error)))
                }
                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(error)))
                }
            }
            val constraints = MediaConstraints()
            if (offer) peer?.createOffer(observer, constraints) else peer?.createAnswer(observer, constraints)
        }

    private suspend fun setRemoteDescription(description: SessionDescription) =
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            peer?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onCreateFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(error)))
                }
                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(error)))
                }
            }, description) ?: continuation.resumeWith(Result.failure(IllegalStateException("Peer connection is unavailable.")))
        }

    private fun startSignalPolling(caller: Boolean) {
        callStatusSubscription?.close()
        callStatusSubscription = SupabaseRealtime.subscribe("call_sessions", "id=eq.$callId", { row ->
            val terminal = row.optString("status")
            if (terminal in setOf("declined", "ended", "missed", "failed") && endNotified.compareAndSet(false, true)) {
                scope.launch {
                    stopRingbackTone()
                    onStatus("Call $terminal")
                    onCallEnded(terminal)
                }
            }
        }, { Log.w(TAG, it) })
        signalJob?.cancel()
        signalJob = scope.launch {
            while (isActive && !disposed.get()) {
                try {
                    val call = CallSignalingRepository.getCall(callId)
                    if (call.status in setOf("declined", "ended", "missed", "failed")) {
                        stopRingbackTone()
                        onStatus("Call ${call.status}")
                        onCallEnded(call.status)
                        break
                    }
                    if (caller && call.answerSdp != null && peer?.remoteDescription == null) {
                        stopRingbackTone()
                        setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, call.answerSdp))
                        onStatus("Connecting")
                    }
                    val candidates = CallSignalingRepository.loadIce(callId, lastIceId)
                    candidates.forEach { item ->
                        lastIceId = maxOf(lastIceId, item.id)
                        val ownId = SupabaseAuthRepository.currentUserId()
                        if (item.senderId != ownId) {
                            peer?.addIceCandidate(IceCandidate(item.sdpMid, item.sdpMLineIndex, item.candidate))
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    onError(error.message ?: "Call signaling temporarily failed.")
                }
                delay(1500)
            }
        }
    }

    fun setMuted(muted: Boolean) {
        microphoneMuted = muted
        audioTrack?.setEnabled(!muted)
    }

    fun setSpeaker(enabled: Boolean) {
        speakerOn = enabled
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = enabled
    }

    fun setVideoEnabled(enabled: Boolean) {
        videoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (capturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    private fun startRingbackTone() {
        stopRingbackTone()
        ringbackTone = runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also { it.startTone(ToneGenerator.TONE_SUP_RINGTONE) }
        }.getOrNull()
    }

    private fun stopRingbackTone() {
        ringbackTone?.let { tone -> runCatching { tone.stopTone() }; runCatching { tone.release() } }
        ringbackTone = null
    }

    suspend fun hangUp(status: String = "ended") {
        if (disposed.getAndSet(true)) return
        stopRingbackTone()
        signalJob?.cancel()
        callStatusSubscription?.close()
        callStatusSubscription = null
        runCatching { CallSignalingRepository.setStatus(callId, status) }
        withContext(Dispatchers.Main) {
            localRenderer?.let { videoTrack?.removeSink(it); it.release() }
            remoteRenderer?.let { renderer ->
                peer?.receivers?.forEach { (it.track() as? VideoTrack)?.removeSink(renderer) }
                renderer.release()
            }
            localRenderer = null
            remoteRenderer = null
            runCatching { capturer?.stopCapture() }
            capturer?.dispose()
            textureHelper?.dispose()
            videoTrack?.dispose()
            videoSource?.dispose()
            audioTrack?.dispose()
            audioSource?.dispose()
            peer?.close()
            peer?.dispose()
            factory?.dispose()
            audioModule?.release()
            eglBase.release()
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.isSpeakerphoneOn = false
            audio.mode = AudioManager.MODE_NORMAL
            peer = null
        }
    }

    companion object { private const val TAG = "WebRtcCallManager" }
}

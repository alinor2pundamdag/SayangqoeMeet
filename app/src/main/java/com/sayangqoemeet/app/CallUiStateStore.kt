package com.sayangqoemeet.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallPhase {
    IDLE,
    RINGING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDING,
    ENDED,
    FAILED
}

data class VideoTrackUiState(
    val trackAvailable: Boolean = false,
    val rendererAttached: Boolean = false,
    val firstFrameReceived: Boolean = false
)

data class CallUiState(
    val callId: String? = null,
    val phase: CallPhase = CallPhase.IDLE,
    val isVideoCall: Boolean = false,
    val isMuted: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isSpeakerEnabled: Boolean = true,
    val localVideo: VideoTrackUiState = VideoTrackUiState(),
    val remoteVideo: VideoTrackUiState = VideoTrackUiState(),
    val errorMessage: String? = null
) {
    val isTerminal: Boolean
        get() = phase == CallPhase.ENDED || phase == CallPhase.FAILED
}

internal class CallUiStateStore {
    private val mutableState = MutableStateFlow(CallUiState())

    val state: StateFlow<CallUiState> = mutableState.asStateFlow()

    @Synchronized
    fun update(transform: (CallUiState) -> CallUiState) {
        mutableState.value = transform(mutableState.value)
    }

    @Synchronized
    fun begin(callId: String, isVideoCall: Boolean) {
        mutableState.value = CallUiState(
            callId = callId,
            phase = CallPhase.CONNECTING,
            isVideoCall = isVideoCall,
            isCameraEnabled = isVideoCall
        )
    }

    @Synchronized
    fun setMuted(muted: Boolean) {
        mutableState.value = mutableState.value.copy(isMuted = muted)
    }

    @Synchronized
    fun setCameraEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(isCameraEnabled = enabled)
    }

    @Synchronized
    fun setSpeakerEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(isSpeakerEnabled = enabled)
    }

    @Synchronized
    fun updateVideoState(local: VideoTrackUiState? = null, remote: VideoTrackUiState? = null) {
        val current = mutableState.value
        mutableState.value = current.copy(
            localVideo = local ?: current.localVideo,
            remoteVideo = remote ?: current.remoteVideo
        )
    }

    @Synchronized
    fun setPhase(phase: CallPhase, error: String? = null) {
        mutableState.value = mutableState.value.copy(
            phase = phase,
            errorMessage = error
        )
    }

    @Synchronized
    fun clear() {
        mutableState.value = CallUiState(phase = CallPhase.ENDED)
    }

    companion object {
        val shared: CallUiStateStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { CallUiStateStore() }
    }
}

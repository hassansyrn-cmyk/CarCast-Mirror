package com.carcast.mirror.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BrowserStatus { STOPPED, STARTING, WAITING_FOR_BROWSER, APPROVAL_REQUIRED, NEGOTIATING, CONNECTED, FAILED }
data class ReceiverUiState(val active: Boolean = false, val pairingCode: String? = null, val expiresAtMs: Long = 0, val port: Int = 0, val status: String = "Stopped", val friendlyName: String = "CarCast Receiver", val pendingSender: String? = null, val pendingSas: String? = null)
fun ReceiverUiState.showsStandalonePairingCode(): Boolean =
    active && status == "Ready to receive" && pendingSender == null && pairingCode?.matches(Regex("\\d{6}")) == true

data class BrowserUiState(val status: BrowserStatus = BrowserStatus.STOPPED, val address: String = "", val httpPort: Int = 0, val signalPort: Int = 0, val pairingCode: String = "", val browserUserAgent: String? = null, val remoteAddress: String? = null, val sessionStartedAtMs: Long? = null, val error: String? = null, val qualityMode: BrowserQualityMode = BrowserQualityMode.AUTO, val sessionId: String? = null)
data class AudioUiState(val enabled: Boolean = true, val status: String = "Audio: Waiting for permission")
data class MirroringUiState(val state: ConnectionState = ConnectionState.IDLE, val protocol: String = "CarCast Native", val codec: String? = null, val width: Int? = null, val height: Int? = null, val fps: Double? = null, val bitrateKbps: Int? = null, val reconnectAttempts: Int = 0, val startedAtMs: Long? = null)
data class LiveDiagnostics(val protocol: String = "Unavailable", val state: String = "Unavailable", val receiver: String = "Unavailable", val codec: String = "Unavailable", val resolution: String = "Unavailable", val captureResolution: String = "Unavailable", val fps: Double? = null, val encodedFps: Double? = null, val bitrateKbps: Int? = null, val bytesPerSecond: Long? = null, val framesSent: Long? = null, val framesEncoded: Long? = null, val framesDecoded: Long? = null, val droppedFrames: Long? = null, val reconnects: Int? = null, val orientation: String = "Unavailable", val sessionSeconds: Long? = null, val iceState: String? = null, val browserUserAgent: String? = null, val selectedCandidatePair: String? = null, val localCandidateType: String? = null, val remoteCandidateType: String? = null, val rttMs: Long? = null, val packetsLost: Long? = null, val availableOutgoingBitrateKbps: Int? = null, val qualityLimitationReason: String? = null, val qualityNote: String? = null, val audioTrackState: String? = null, val audioCodec: String? = null, val audioSampleRate: Int? = null, val audioChannels: Int? = null, val audioBytesSent: Long? = null, val audioPacketsSent: Long? = null, val audioBitrateKbps: Int? = null, val audioPacketsLost: Long? = null, val audioJitterMs: Double? = null, val audioCaptureSource: String? = null)

object AppState {
    private val _receiver = MutableStateFlow(ReceiverUiState()); val receiver: StateFlow<ReceiverUiState> = _receiver.asStateFlow()
    private val _browser = MutableStateFlow(BrowserUiState()); val browser: StateFlow<BrowserUiState> = _browser.asStateFlow()
    private val _mirror = MutableStateFlow(MirroringUiState()); val mirror: StateFlow<MirroringUiState> = _mirror.asStateFlow()
    private val _diagnostics = MutableStateFlow(LiveDiagnostics()); val diagnostics: StateFlow<LiveDiagnostics> = _diagnostics.asStateFlow()
    private val _debug = MutableStateFlow(BrowserDebugReport()); val debug: StateFlow<BrowserDebugReport> = _debug.asStateFlow()
    private val _audio = MutableStateFlow(AudioUiState()); val audio: StateFlow<AudioUiState> = _audio.asStateFlow()
    private val _nativeReceiver = MutableStateFlow(NativeReceiverMetrics()); val nativeReceiver: StateFlow<NativeReceiverMetrics> = _nativeReceiver.asStateFlow()
    fun receiver(update: (ReceiverUiState) -> ReceiverUiState) { _receiver.value = update(_receiver.value) }
    fun browser(update: (BrowserUiState) -> BrowserUiState) { _browser.value = update(_browser.value) }
    fun mirror(update: (MirroringUiState) -> MirroringUiState) { _mirror.value = update(_mirror.value) }
    fun diagnostics(update: (LiveDiagnostics) -> LiveDiagnostics) { _diagnostics.value = update(_diagnostics.value) }
    fun setDebug(report: BrowserDebugReport) { _debug.value = report }
    fun audio(update: (AudioUiState) -> AudioUiState) { _audio.value = update(_audio.value) }
    fun nativeReceiver(update: (NativeReceiverMetrics) -> NativeReceiverMetrics) { _nativeReceiver.value = update(_nativeReceiver.value) }
    fun resetBrowserReceiverState() { _browser.value = BrowserUiState(); _diagnostics.value = LiveDiagnostics() }
    fun failBrowserReceiver(message: String) { _browser.value = _browser.value.copy(status = BrowserStatus.FAILED, address = "", httpPort = 0, signalPort = 0, remoteAddress = null, browserUserAgent = null, sessionStartedAtMs = null, error = message) }
}

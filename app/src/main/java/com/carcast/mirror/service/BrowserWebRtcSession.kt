package com.carcast.mirror.service

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.carcast.mirror.core.*
import org.json.JSONObject
import org.webrtc.*

class BrowserWebRtcSession(private val context: Context, private val send: (String) -> Unit, private val audioEnabled: Boolean = true, private val onFailure: (String) -> Unit = {}) {
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private val peer: PeerConnection
    private var capturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var track: VideoTrack? = null
    private var sender: RtpSender? = null
    private var audioSender: RtpSender? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var audioBridge: PlaybackCaptureAudioDeviceModule? = null
    private var audioReady = false
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var remoteDescriptionSet = false
    private val queuedRemoteCandidates = mutableListOf<IceCandidate>()
    private val statsHandler = Handler(Looper.getMainLooper())
    private var previousBytes = 0L
    private var previousFrames = 0L
    private var previousAudioBytes = 0L
    private var previousStatsMs = 0L
    private var stopped = false
    private var qualityMode = BrowserQualityMode.AUTO
    private var appliedMode = BrowserQualityMode.HD
    private var adaptation = QualityAdaptationState()
    private var lastPeerState = PeerConnection.PeerConnectionState.NEW
    private var lastIceState = PeerConnection.IceConnectionState.NEW
    private val recoveryTimeout = Runnable {
        if (!stopped && (lastPeerState == PeerConnection.PeerConnectionState.DISCONNECTED || lastIceState == PeerConnection.IceConnectionState.DISCONNECTED)) {
            fail("WEBRTC_RECOVERY_TIMEOUT", IllegalStateException("TV connection did not recover"))
        }
    }

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).setEnableInternalTracer(false).createInitializationOptions())
        val bridge = if (audioEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runCatching { PlaybackCaptureAudioDeviceModule(context) { status -> AppState.audio { it.copy(status = status) } }.also { if (!it.install()) throw IllegalStateException("WebRTC audio input unavailable") } }
                .getOrElse { AppState.audio { it.copy(status = "Audio: Unavailable — video only") }; null }
        } else null
        audioBridge = bridge
        val builder = PeerConnectionFactory.builder().setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)).setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
        bridge?.let { builder.setAudioDeviceModule(it.audioDeviceModule) }
        factory = builder.createPeerConnectionFactory()
        peer = factory.createPeerConnection(emptyList(), observer()) ?: error("PeerConnection unavailable")
    }

    fun startCapture(resultCode: Int, data: Intent) {
        DebugDiagnostics.stage(STAGE_SCREEN_CAPTURE_START_REQUESTED)
        surfaceHelper = SurfaceTextureHelper.create("CarCastBrowserCapture", egl.eglBaseContext)
        videoSource = factory.createVideoSource(false)
        capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() { override fun onStop() { stop() } })
        DebugDiagnostics.stage(STAGE_SCREEN_CAPTURER_CREATED)
        capturer!!.initialize(surfaceHelper, context, videoSource!!.capturerObserver)
        val size = captureSize(appliedMode.profile())
        AppState.diagnostics { it.copy(captureResolution = "${size.first}×${size.second}") }
        capturer!!.startCapture(size.first, size.second, appliedMode.profile().fps)
        DebugDiagnostics.stage(STAGE_SCREEN_CAPTURE_STARTED)
        val sharedProjection = capturer!!.mediaProjection
        if (sharedProjection != null) {
            if (!ProjectionLifecycle.projectionRetrieved() || !ProjectionLifecycle.virtualDisplayCreated()) {
                throw IllegalStateException("Duplicate MediaProjection ownership detected before audio initialization")
            }
        }
        if (audioBridge != null && sharedProjection != null) {
            audioReady = audioBridge!!.attachProjection(sharedProjection)
            if (!audioReady) AppState.audio { it.copy(status = "Audio: Unavailable — projection attachment failed") }
        } else if (audioBridge != null) {
            AppState.audio { it.copy(status = "Audio: Unavailable — projection not ready") }
        }
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) {}
            override fun onDisplayRemoved(id: Int) {}
            override fun onDisplayChanged(id: Int) { if (id == android.view.Display.DEFAULT_DISPLAY) reconfigureCapture() }
        }
        dm.registerDisplayListener(displayListener, null)
        track = factory.createVideoTrack("carcast-screen", videoSource)
        sender = peer.addTrack(track, listOf("carcast-stream"))
        if (audioReady) {
            audioSource = factory.createAudioSource(MediaConstraints())
            audioTrack = factory.createAudioTrack("carcast-playback-audio", audioSource)
            audioSender = peer.addTrack(audioTrack, listOf("carcast-stream"))
            AppState.audio { it.copy(status = "Audio: WebRTC track connected") }
            AppState.diagnostics { it.copy(audioTrackState = "Connected", audioCaptureSource = "DEVICE_PLAYBACK", audioSampleRate = 48_000, audioChannels = 2) }
        }
        DebugDiagnostics.stage(STAGE_VIDEO_TRACK_STARTED)
        applySenderParameters(appliedMode.profile())
        pollStats()
        peer.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) { fail("OFFER_CREATED", IllegalStateException("empty SDP offer")); return }
                DebugDiagnostics.stage(STAGE_OFFER_CREATED)
                peer.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() { Log.i(TAG, "offer sent"); send(JSONObject().put("type", "offer").put("sdp", desc.description).toString()) }
                    override fun onSetFailure(error: String?) { fail("OFFER_LOCAL_DESCRIPTION", IllegalStateException(error ?: "local SDP failed")) }
                }, desc)
            }
            override fun onCreateFailure(error: String?) { fail("OFFER_CREATED", IllegalStateException(error ?: "offer creation failed")) }
        }, MediaConstraints())
    }

    fun setQuality(mode: BrowserQualityMode) {
        qualityMode = mode
        if (mode != BrowserQualityMode.AUTO) appliedMode = mode
        else if (appliedMode == BrowserQualityMode.AUTO) appliedMode = BrowserQualityMode.HD
        AppState.browser { it.copy(qualityMode = mode) }
        if (!stopped) {
            applySenderParameters(appliedMode.profile())
            reconfigureCapture()
        }
    }

    fun setAudioEnabled(enabled: Boolean) { audioTrack?.setEnabled(enabled); AppState.audio { it.copy(enabled = enabled, status = if (enabled) "DEVICE_PLAYBACK • WebRTC Opus" else "Video only — device audio disabled") } }

    fun handle(message: JSONObject) {
        when (message.optString("type")) {
            "answer" -> peer.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() { remoteDescriptionSet = true; DebugDiagnostics.stage(STAGE_REMOTE_ANSWER_SET); synchronized(queuedRemoteCandidates) { queuedRemoteCandidates.forEach(peer::addIceCandidate); queuedRemoteCandidates.clear() }; Log.i(TAG, "answer received") }
                override fun onSetFailure(error: String?) { fail("REMOTE_ANSWER_SET", IllegalStateException(error ?: "remote answer rejected")) }
            }, SessionDescription(SessionDescription.Type.ANSWER, message.getString("sdp")))
            "candidate" -> { val candidate = IceCandidate(message.optString("sdpMid"), message.optInt("sdpMLineIndex"), message.getString("candidate")); if (remoteDescriptionSet) peer.addIceCandidate(candidate) else synchronized(queuedRemoteCandidates) { queuedRemoteCandidates.add(candidate) } }
        }
    }

    private fun observer() = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) { send(JSONObject().put("type", "candidate").put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex).put("candidate", c.sdp).toString()) }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            lastPeerState = state
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> { statsHandler.removeCallbacks(recoveryTimeout); AppState.browser { it.copy(status = BrowserStatus.CONNECTED, error = null) } }
                PeerConnection.PeerConnectionState.DISCONNECTED -> { AppState.browser { it.copy(status = BrowserStatus.NEGOTIATING, error = "Connection interrupted — trying to recover") }; statsHandler.removeCallbacks(recoveryTimeout); statsHandler.postDelayed(recoveryTimeout, 8_000) }
                PeerConnection.PeerConnectionState.FAILED -> fail("WEBRTC_CONNECTION", IllegalStateException("TV connection failed"))
                else -> AppState.browser { it.copy(status = BrowserStatus.NEGOTIATING) }
            }
            AppState.diagnostics { it.copy(state = state.name, protocol = "WebRTC browser") }
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            lastIceState = state
            if (state == PeerConnection.IceConnectionState.CHECKING) DebugDiagnostics.stage(STAGE_ICE_CONNECTING)
            if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) { DebugDiagnostics.stage(STAGE_ICE_CONNECTED); statsHandler.removeCallbacks(recoveryTimeout) }
            if (state == PeerConnection.IceConnectionState.DISCONNECTED) { AppState.browser { it.copy(status = BrowserStatus.NEGOTIATING, error = "Connection interrupted — trying to recover") }; statsHandler.removeCallbacks(recoveryTimeout); statsHandler.postDelayed(recoveryTimeout, 8_000) }
            if (state == PeerConnection.IceConnectionState.FAILED) fail("ICE_CONNECTION", IllegalStateException("TV ICE connection failed"))
            AppState.diagnostics { it.copy(iceState = state.name) }
        }
        override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}
        override fun onAddStream(p0: MediaStream) {}
        override fun onRemoveStream(p0: MediaStream) {}
        override fun onDataChannel(p0: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(p0: RtpReceiver, p1: Array<out MediaStream>) {}
        override fun onTrack(p0: RtpTransceiver) {}
    }

    private fun pollStats() {
        if (stopped) return
        peer.getStats(object : RTCStatsCollectorCallback {
            override fun onStatsDelivered(report: RTCStatsReport) {
                var bytes = 0L; var audioBytes = 0L; var audioPackets = 0L; var audioLost: Long? = null; var audioJitter: Double? = null; var audioCodec: String? = null; var audioRate: Int? = null; var audioChannels: Int? = null; var framesEncoded = 0L; var width: Int? = null; var height: Int? = null; var fps: Double? = null; var rtt: Double? = null; var lost: Long? = null; var codec: String? = null; var candidatePair: String? = null; var localType: String? = null; var remoteType: String? = null; var available: Long? = null; var limitation: String? = null
                report.statsMap.values.forEach { stat ->
                    val m = stat.members
                    when (stat.type) {
                        "outbound-rtp" -> if (m["kind"] == "video" || m["mediaType"] == "video") { bytes += (m["bytesSent"] as? Number)?.toLong() ?: 0; framesEncoded += (m["framesEncoded"] as? Number)?.toLong() ?: 0; width = (m["frameWidth"] as? Number)?.toInt(); height = (m["frameHeight"] as? Number)?.toInt(); fps = (m["framesPerSecond"] as? Number)?.toDouble(); limitation = m["qualityLimitationReason"]?.toString() } else if (m["kind"] == "audio" || m["mediaType"] == "audio") { audioBytes += (m["bytesSent"] as? Number)?.toLong() ?: 0; audioPackets += (m["packetsSent"] as? Number)?.toLong() ?: 0; audioLost = (m["packetsLost"] as? Number)?.toLong(); audioJitter = (m["jitter"] as? Number)?.toDouble() }
                        "candidate-pair" -> if (m["state"] == "succeeded" && (m["nominated"] == true || m["selected"] == true)) { rtt = (m["currentRoundTripTime"] as? Number)?.toDouble(); candidatePair = stat.id; available = (m["availableOutgoingBitrate"] as? Number)?.toLong() }
                        "transport" -> available = available ?: (m["availableOutgoingBitrate"] as? Number)?.toLong()
                        "local-candidate" -> localType = m["candidateType"]?.toString()
                        "remote-candidate" -> remoteType = m["candidateType"]?.toString()
                        "codec" -> { val mime = m["mimeType"]?.toString(); if (mime?.lowercase()?.startsWith("audio/") == true) { audioCodec = mime; audioRate = (m["clockRate"] as? Number)?.toInt(); audioChannels = (m["channels"] as? Number)?.toInt() } else codec = mime }
                        "remote-inbound-rtp" -> lost = (m["packetsLost"] as? Number)?.toLong()
                    }
                }
                val now = System.currentTimeMillis(); val elapsed = (now - previousStatsMs).coerceAtLeast(1); val rate = if (previousStatsMs > 0) ((bytes - previousBytes) * 1000L / elapsed) else null; val audioRateBytes = if (previousStatsMs > 0) ((audioBytes - previousAudioBytes) * 1000L / elapsed) else null; val encodedRate = if (previousStatsMs > 0) ((framesEncoded - previousFrames) * 1000.0 / elapsed) else null
                previousBytes = bytes; previousAudioBytes = audioBytes; previousFrames = framesEncoded; previousStatsMs = now
                val rttMs = rtt?.let { (it * 1000).toLong() }
                val qualityReason = limitation?.takeIf { it != "none" }
                    AppState.diagnostics { it.copy(codec = codec ?: it.codec, resolution = if (width != null && height != null) "$width×$height" else it.resolution, encodedFps = encodedRate, fps = fps, bytesPerSecond = rate, framesSent = framesEncoded, framesEncoded = framesEncoded, bitrateKbps = rate?.let { (it * 8 / 1000).toInt() }, rttMs = rttMs, packetsLost = lost, selectedCandidatePair = candidatePair, localCandidateType = localType, remoteCandidateType = remoteType, availableOutgoingBitrateKbps = available?.div(1000)?.toInt(), qualityLimitationReason = qualityReason, audioBytesSent = audioBytes, audioPacketsSent = audioPackets, audioBitrateKbps = audioRateBytes?.let { (it * 8 / 1000).toInt() }, audioPacketsLost = audioLost, audioJitterMs = audioJitter?.let { it * 1000.0 }, audioCodec = audioCodec ?: it.audioCodec, audioSampleRate = audioRate ?: it.audioSampleRate, audioChannels = audioChannels ?: it.audioChannels, audioCaptureSource = if (audioTrack != null) "DEVICE_PLAYBACK" else it.audioCaptureSource, qualityNote = when { qualityReason == "bandwidth" -> "Network bandwidth limiting quality"; qualityReason == "cpu" -> "Phone encoder performance limiting quality"; fps?.let { it > 0.0 && it < appliedMode.profile().fps * 0.8 } == true -> "Reducing resolution to protect 30 FPS"; qualityMode == BrowserQualityMode.FULL_HD && ((width ?: 0) < 1280 || (height ?: 0) < 720) -> "HD fallback — device/network limitation"; else -> null }) }
                adapt(rttMs, lost, fps ?: 0.0, qualityReason)
                statsHandler.postDelayed({ pollStats() }, 2000)
            }
        })
    }

    private fun adapt(rttMs: Long?, lost: Long?, fps: Double, limitation: String?) {
        adaptation = QualityAdaptation.next(adaptation, qualityMode, rttMs, lost, fps, appliedMode.profile().fps)
        val severeLowFps = fps > 0.0 && fps < appliedMode.profile().fps * 0.5
        val selectedCeiling = if (qualityMode == BrowserQualityMode.AUTO) BrowserQualityMode.FULL_HD else qualityMode
        val shouldReduce = severeLowFps || adaptation.constrainedSamples >= 2
        if (shouldReduce && appliedMode != BrowserQualityMode.LOW_LATENCY) {
            appliedMode = if (appliedMode == BrowserQualityMode.FULL_HD) BrowserQualityMode.HD else BrowserQualityMode.LOW_LATENCY
            adaptation = QualityAdaptationState(); applySenderParameters(appliedMode.profile()); reconfigureCapture()
        } else if (adaptation.healthySamples >= 5 && limitation.isNullOrBlank() && appliedMode != selectedCeiling) {
            appliedMode = if (appliedMode == BrowserQualityMode.LOW_LATENCY) BrowserQualityMode.HD else BrowserQualityMode.FULL_HD
            adaptation = QualityAdaptationState(); applySenderParameters(appliedMode.profile()); reconfigureCapture()
        }
    }

    private fun applySenderParameters(profile: BrowserQualityProfile) {
        val current = sender ?: return
        runCatching { val params = current.parameters; params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE; params.encodings.forEach { it.maxBitrateBps = profile.targetBitrateBps; it.minBitrateBps = profile.minBitrateBps; it.maxFramerate = profile.fps; it.active = true }; if (!current.setParameters(params)) Log.w(TAG, "WebRTC rejected sender parameters") }.onFailure { DebugDiagnostics.error("SENDER_PARAMETERS", it) }
    }

    private fun reconfigureCapture() {
        val size = captureSize(appliedMode.profile()); AppState.diagnostics { it.copy(captureResolution = "${size.first}×${size.second}") }
        runCatching { capturer?.changeCaptureFormat(size.first, size.second, appliedMode.profile().fps) }.onFailure { DebugDiagnostics.error("ROTATION_OR_QUALITY_RECONFIGURE", it) }
    }

    private fun captureSize(profile: BrowserQualityProfile): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics; val aspect = metrics.widthPixels.toDouble() / metrics.heightPixels.toDouble(); val landscape = aspect >= 1.0; val maxW = if (landscape) profile.maxWidth else profile.maxHeight; val maxH = if (landscape) profile.maxHeight else profile.maxWidth
        val w: Int; val h: Int
        if (aspect >= maxW.toDouble() / maxH) { w = maxW; h = (w / aspect).toInt() } else { h = maxH; w = (h * aspect).toInt() }
        return (w.coerceAtLeast(2) / 2 * 2) to (h.coerceAtLeast(2) / 2 * 2)
    }

    private fun fail(stage: String, throwable: Throwable) { val reason = "WEBRTC ${stage.replace('_', ' ')} FAILED: ${throwable.message ?: throwable::class.java.simpleName}"; DebugDiagnostics.error(stage, throwable); AppState.failBrowserReceiver(reason); onFailure(reason) }
    fun stop() { if (stopped) return; stopped = true; statsHandler.removeCallbacksAndMessages(null); val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager; displayListener?.let { runCatching { dm.unregisterDisplayListener(it) } }; runCatching { capturer?.stopCapture() }; runCatching { capturer?.dispose() }; runCatching { surfaceHelper?.dispose() }; runCatching { track?.dispose() }; runCatching { sender?.dispose() }; runCatching { videoSource?.dispose() }; runCatching { peer.close() }; runCatching { factory.dispose() }; runCatching { egl.release() }; runCatching { audioBridge?.release() }; AppState.diagnostics { it.copy(state = "DISCONNECTED") } }
    companion object { private const val TAG = "CarCastWebRTC" }
}

open class SdpObserverAdapter : SdpObserver { override fun onCreateSuccess(p0: SessionDescription?) {}; override fun onSetSuccess() {}; override fun onCreateFailure(p0: String?) {}; override fun onSetFailure(p0: String?) {} }

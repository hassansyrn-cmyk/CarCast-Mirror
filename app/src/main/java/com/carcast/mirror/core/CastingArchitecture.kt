package com.carcast.mirror.core

import java.net.InetAddress

enum class CastProtocol { CARCAST, WEBRTC_BROWSER, MIRACAST_SYSTEM, GOOGLE_CAST, DLNA, RTSP, HLS }
enum class ReceiverCapability { FULL_SCREEN_MIRRORING, MEDIA_ONLY, AUDIO, PARKED_ONLY }
data class CastTarget(val id: String, val name: String, val protocol: CastProtocol, val host: InetAddress? = null, val port: Int? = null, val capabilities: Set<ReceiverCapability>, val recommended: Boolean = false, val details: String = "")
interface CastDiscoveryProvider { val protocol: CastProtocol; fun start(onTargets: (List<CastTarget>) -> Unit); fun stop() }
interface CastSession { val state: kotlinx.coroutines.flow.StateFlow<ConnectionState>; fun stop() }

data class DiagnosticsSnapshot(val resolution: String = "—", val encoder: String = "—", val fps: Double = 0.0, val bitrateKbps: Int = 0, val networkKbps: Int = 0, val latencyMs: Long = 0, val droppedFrames: Long = 0, val decoderFps: Double = 0.0, val connectionType: String = "Local network", val receiverType: String = "—", val reconnects: Int = 0, val startedAtMs: Long? = null) {
    val durationSeconds: Long get() = startedAtMs?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0
}

package com.carcast.mirror.core

import java.net.InetAddress

enum class CastProtocol { CARCAST, WEBRTC_BROWSER, MIRACAST_SYSTEM, GOOGLE_CAST, DLNA, RTSP, HLS }
enum class ReceiverCapability { FULL_SCREEN_MIRRORING, MEDIA_ONLY, AUDIO, PARKED_ONLY }
data class CastTarget(val id: String, val name: String, val protocol: CastProtocol, val host: InetAddress? = null, val port: Int? = null, val capabilities: Set<ReceiverCapability>, val recommended: Boolean = false, val details: String = "")
interface CastDiscoveryProvider { val protocol: CastProtocol; fun start(onTargets: (List<CastTarget>) -> Unit); fun stop() }

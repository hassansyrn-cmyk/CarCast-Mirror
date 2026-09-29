package com.carcast.mirror.service

import android.app.Service
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.view.Surface
import android.view.SurfaceHolder
import com.carcast.mirror.core.*
import com.carcast.mirror.discovery.SERVICE_TYPE
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.Executors

class ReceiverService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var codec: MediaCodec? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { startReceiver(); return START_STICKY }

    private fun startReceiver() {
        if (server != null) return
        executor.execute {
            runCatching {
                val identity = TlsIdentity.serverIdentity()
                server = TlsIdentity.serverSocket(identity)
                server!!.soTimeout = 1000
                val pin = identity.sas
                ReceiverSessionState.begin(pin)
                publish(pin)
                while (!Thread.currentThread().isInterrupted) {
                    val socket = try { server!!.accept() } catch (_: SocketTimeoutException) { continue }
                    runCatching { authenticateAndDecode(socket) }
                        .onSuccess {
                            socket.close()
                            updateUi(pin)
                        }
                        .onFailure {
                            socket.close()
                            AppState.receiver { state -> state.copy(status = "Connection failed") }
                        }
                }
            }.onFailure {
                AppState.receiver { state -> state.copy(active = false, status = "Failed") }
                stopSelf()
            }
        }
    }

    private fun publish(pin: String) {
        val port = server!!.localPort
        AppState.receiver { it.copy(active = true, pairingCode = pin, expiresAtMs = ReceiverSessionState.expiresAtMs, port = port, status = "Ready to connect") }
        val nsd = getSystemService(NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = "CarCast ${android.os.Build.MODEL}"
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("protocol", "1")
            setAttribute("capabilities", "secure,avc")
        }
        registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
    }

    private fun updateUi(pin: String) { AppState.receiver { it.copy(pairingCode = pin, expiresAtMs = ReceiverSessionState.expiresAtMs, status = "Ready to connect") } }

    private fun authenticateAndDecode(socket: java.net.Socket) {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        val channel = TlsFramedChannel(input, output)
        val capabilities = localCapabilities()
        channel.write(FrameTypes.CAPABILITIES, capabilities.encode())
        val proposalFrame = channel.read()
        require(proposalFrame.type == FrameTypes.SENDER_PROPOSAL)
        val proposed = StreamConfig.decode(proposalFrame.payload)
        val accepted = if (isSupported(proposed, capabilities)) proposed else counterProposal(proposed)
        if (accepted == proposed) channel.write(FrameTypes.RECEIVER_ACCEPT, accepted.encode()) else { channel.write(FrameTypes.RECEIVER_COUNTER_PROPOSAL, accepted.encode()); require(channel.read().type == FrameTypes.RECEIVER_ACCEPT) }
        val config = accepted
        AppState.receiver { it.copy(status = "Connected") }
        decode(channel, config)
    }

    private fun localCapabilities(): ReceiverCapabilities {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        val avc = infos.filter { info -> info.supportedTypes.any { type -> type.equals("video/avc", true) } }
        val hevc = infos.filter { info -> info.supportedTypes.any { type -> type.equals("video/hevc", true) } }
        val avcInfo = avc.firstOrNull()
        val bounds = if (avcInfo != null) runCatching {
            val caps = avcInfo.getCapabilitiesForType("video/avc").videoCapabilities
            caps.supportedWidths.upper to caps.supportedHeights.upper
        }.getOrDefault(854 to 480) else 0 to 0
        val fps = if (avcInfo != null) runCatching {
            avcInfo.getCapabilitiesForType("video/avc").videoCapabilities.supportedFrameRates.upper.toInt()
        }.getOrDefault(30) else 0
        return ReceiverCapabilities(
            codecs = listOfNotNull("video/avc".takeIf { avc.isNotEmpty() }, "video/hevc".takeIf { hevc.isNotEmpty() }),
            maxWidth = bounds.first,
            maxHeight = bounds.second,
            maxFps = fps,
            maxBitrate = 0,
            supportsAvc = avc.isNotEmpty(),
            supportsHevc = hevc.isNotEmpty()
        )
    }

    private fun codecCapabilities(codec: String): MediaCodecInfo.VideoCapabilities? = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }.firstOrNull { info -> info.supportedTypes.any { it.equals(codec, true) } }?.getCapabilitiesForType(codec)?.videoCapabilities
    private fun isSupported(config: StreamConfig, capabilities: ReceiverCapabilities): Boolean { val caps = codecCapabilities(config.codec) ?: return false; return config.codec in capabilities.codecs && config.width % caps.widthAlignment == 0 && config.height % caps.heightAlignment == 0 && caps.areSizeAndRateSupported(config.width, config.height, config.fps.toDouble()) }
    private fun counterProposal(original: StreamConfig): StreamConfig { val caps = codecCapabilities(original.codec) ?: throw IllegalArgumentException("unsupported codec"); val candidates = listOf(original.width to original.height, 1920 to 1080, 1280 to 720, 854 to 480); val fps = minOf(original.fps, caps.supportedFrameRates.upper.toInt()); val chosen = candidates.map { (w, h) -> (w / caps.widthAlignment) * caps.widthAlignment to (h / caps.heightAlignment) * caps.heightAlignment }.firstOrNull { (w, h) -> caps.areSizeAndRateSupported(w, h, fps.toDouble()) } ?: throw IllegalArgumentException("unsupported resolution/rate"); return original.copy(width = chosen.first, height = chosen.second, fps = fps) }

    private fun decode(channel: TlsFramedChannel, config: StreamConfig) {
        val initial = ReceiverSurfaceHolder.awaitSurface(10_000) ?: return
        configureDecoder(config, initial)
        ReceiverSurfaceHolder.onSurfaceChanged = { next ->
            synchronized(this) {
                if (next == null) releaseCodec() else if (codec == null) configureDecoder(config, next)
            }
        }
        try {
            while (!Thread.currentThread().isInterrupted) {
                val frame = channel.read()
                if (frame.type == FrameTypes.STREAM_CONFIG) {
                    val nextConfig = StreamConfig.decode(frame.payload)
                    val nextSurface = ReceiverSurfaceHolder.surface?.takeIf { it.isValid }
                    if (nextSurface != null) synchronized(this) { configureDecoder(nextConfig, nextSurface) }
                    continue
                }
                if (frame.type != FrameTypes.ENCODED_FRAME) continue
                val packet = EncodedFrame.decode(frame.payload)
                synchronized(this) {
                    val active = codec
                    if (active != null) {
                        val index = active.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            active.getInputBuffer(index)!!.apply { clear(); put(packet.payload) }
                            active.queueInputBuffer(index, 0, packet.payload.size, packet.ptsUs, packet.flags)
                        }
                        val info = MediaCodec.BufferInfo()
                        val output = active.dequeueOutputBuffer(info, 10_000)
                        if (output >= 0) active.releaseOutputBuffer(output, true)
                    }
                }
            }
        } finally {
            ReceiverSurfaceHolder.onSurfaceChanged = null
            synchronized(this) { releaseCodec() }
        }
    }

    private fun configureDecoder(config: StreamConfig, surface: Surface) {
        releaseCodec()
        codec = MediaCodec.createDecoderByType(config.codec).apply {
            configure(MediaFormat.createVideoFormat(config.codec, config.width, config.height), surface, null, 0)
            start()
        }
    }

    private fun releaseCodec() { runCatching { codec?.stop() }; runCatching { codec?.release() }; codec = null }

    override fun onDestroy() {
        ReceiverSessionState.clear()
        AppState.receiver { it.copy(active = false, status = "Stopped", pairingCode = null) }
        executor.shutdownNow()
        server?.close()
        synchronized(this) { releaseCodec() }
        val nsd = getSystemService(NSD_SERVICE) as NsdManager
        registration?.let { listener -> runCatching { nsd.unregisterService(listener) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object ReceiverSurfaceHolder : SurfaceHolder.Callback {
    @Volatile var surface: Surface? = null
    @Volatile var onSurfaceChanged: ((Surface?) -> Unit)? = null
    private val lock = Object()
    fun attach(holder: SurfaceHolder) { holder.addCallback(this); if (holder.surface?.isValid == true) { surface = holder.surface; synchronized(lock) { lock.notifyAll() } } }
    fun awaitSurface(timeoutMs: Long): Surface? { synchronized(lock) { if (surface?.isValid != true) lock.wait(timeoutMs) }; return surface?.takeIf { it.isValid } }
    override fun surfaceCreated(holder: SurfaceHolder) { surface = holder.surface; onSurfaceChanged?.invoke(surface); synchronized(lock) { lock.notifyAll() } }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { surface = holder.surface }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surface = null; onSurfaceChanged?.invoke(null) }
}

object ReceiverSessionState {
    @Volatile var pairingCode: String? = null
    @Volatile var expiresAtMs: Long = 0
    fun begin(code: String) { pairingCode = code; expiresAtMs = Long.MAX_VALUE }
    fun clear() { pairingCode = null; expiresAtMs = 0 }
}

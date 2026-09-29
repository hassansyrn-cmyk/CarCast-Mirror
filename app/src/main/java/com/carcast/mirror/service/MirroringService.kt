package com.carcast.mirror.service

import android.app.*
import android.content.*
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.*
import android.view.Surface
import com.carcast.mirror.core.*
import java.io.*
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class MirroringService : Service() {
    private var projection: MediaProjection? = null; private var display: VirtualDisplay? = null; private var encoder: MediaCodec? = null; private var inputSurface: Surface? = null; private var socket: Socket? = null; private var worker: Thread? = null; private var currentConfig: StreamConfig? = null; private val codecConfigPackets = mutableListOf<ByteArray>(); private var lastOrientation = -1
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }; startForeground(7, notification()); startCapture(intent!!); return START_NOT_STICKY }

    private fun startCapture(intent: Intent) {
        if (running.getAndSet(true)) return
        worker = Thread {
            runCatching {
                val sas = intent.getStringExtra("pin") ?: error("SAS required"); val host = intent.getStringExtra("host") ?: error("Receiver host required"); val port = intent.getIntExtra("port", 0); val policy = ReconnectPolicy(); var negotiated: StreamConfig? = null
                while (running.get()) {
                    try {
                        socket = TlsIdentity.clientSocket(host, port).apply { soTimeout = 15_000; tcpNoDelay = true }
                        val tls = socket as javax.net.ssl.SSLSocket; val certificate = tls.session.peerCertificates.first(); require(TlsIdentity.sas(certificate) == sas.filter(Char::isDigit))
                        val input = DataInputStream(BufferedInputStream(socket!!.getInputStream())); val output = DataOutputStream(BufferedOutputStream(socket!!.getOutputStream())); val channel = TlsFramedChannel(input, output); val capabilitiesFrame = channel.read(); require(capabilitiesFrame.type == FrameTypes.CAPABILITIES); var cfg = negotiated ?: chooseConfig(ReceiverCapabilities.decode(capabilitiesFrame.payload)); channel.write(FrameTypes.SENDER_PROPOSAL, cfg.encode()); val response = channel.read(); if (response.type == FrameTypes.RECEIVER_COUNTER_PROPOSAL) { cfg = StreamConfig.decode(response.payload); channel.write(FrameTypes.RECEIVER_ACCEPT, cfg.encode()) } else require(response.type == FrameTypes.RECEIVER_ACCEPT); negotiated = cfg; if (encoder == null) startEncoder(intent, cfg) else { codecConfigPackets.forEach { channel.write(FrameTypes.ENCODED_FRAME, it) }; encoder?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }; policy.reset(); stream(channel)
                    } catch (failure: Throwable) {
                        socket?.close(); drainEncoderDiscard(); val delay = policy.nextDelayMs() ?: throw failure; Thread.sleep(delay)
                    }
                }
            }.onFailure { stopSelf() }
        }.also { it.start() }
    }

    private fun chooseConfig(c: ReceiverCapabilities): StreamConfig { val m = resources.displayMetrics; val aspect = m.widthPixels.toDouble() / m.heightPixels.toDouble(); val maxW = minOf(m.widthPixels, c.maxWidth); val maxH = minOf(m.heightPixels, c.maxHeight); val w = (if (maxW >= 1920 && maxH >= 1080) 1920 else if (maxW >= 1280 && maxH >= 720) 1280 else 854).coerceAtMost(maxW) / 2 * 2; val h = ((w / aspect).toInt().coerceAtMost(maxH) / 2 * 2); val bitrate = if (c.maxBitrate > 0) minOf(c.maxBitrate, if (w >= 1920) 8_000_000 else if (w >= 1280) 4_000_000 else 2_000_000) else if (w >= 1920) 8_000_000 else if (w >= 1280) 4_000_000 else 2_000_000; return StreamConfig(codec = if (c.supportsAvc) "video/avc" else c.codecs.first(), width = w, height = h, orientation = resources.configuration.orientation, fps = minOf(30, c.maxFps), bitrate = bitrate, audio = false, lowLatency = true) }

    private fun startEncoder(intent: Intent, cfg: StreamConfig) { currentConfig = cfg; lastOrientation = resources.configuration.orientation; val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; projection = manager.getMediaProjection(intent.getIntExtra("result", Activity.RESULT_CANCELED), intent.getParcelableExtra("data")!!); projection!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, Handler(Looper.getMainLooper())); val metrics = resources.displayMetrics; encoder = newEncoder(cfg); inputSurface = encoder!!.createInputSurface(); encoder!!.start(); display = projection!!.createVirtualDisplay("CarCastMirror", cfg.width, cfg.height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, null) }
    private fun newEncoder(cfg: StreamConfig): MediaCodec = MediaCodec.createEncoderByType(cfg.codec).apply { configure(MediaFormat.createVideoFormat(cfg.codec, cfg.width, cfg.height).apply { setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); setInteger(MediaFormat.KEY_BIT_RATE, cfg.bitrate); setInteger(MediaFormat.KEY_FRAME_RATE, cfg.fps); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
    private fun reconfigureEncoder(cfg: StreamConfig) { val old = encoder; val oldSurface = inputSurface; val replacement = newEncoder(cfg); val replacementSurface = replacement.createInputSurface(); replacement.start(); val metrics = resources.displayMetrics; display?.resize(cfg.width, cfg.height, metrics.densityDpi); display?.setSurface(replacementSurface); encoder = replacement; inputSurface = replacementSurface; old?.runCatching { stop(); release() }; oldSurface?.release(); currentConfig = cfg; codecConfigPackets.clear() }

    private fun stream(channel: TlsFramedChannel) {
        val info = MediaCodec.BufferInfo()
        while (running.get() && !Thread.currentThread().isInterrupted) {
            if (resources.configuration.orientation != lastOrientation) {
                lastOrientation = resources.configuration.orientation
                currentConfig?.let { cfg ->
                    val rotated = cfg.copy(width = cfg.height, height = cfg.width, orientation = lastOrientation)
                    reconfigureEncoder(rotated)
                    channel.write(FrameTypes.STREAM_CONFIG, rotated.encode())
                    encoder?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
                }
            }
            val index = encoder?.dequeueOutputBuffer(info, 10_000) ?: return
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = encoder!!.outputFormat
                    listOf("csd-0", "csd-1").forEach { key ->
                        format.getByteBuffer(key)?.let { b ->
                            val bytes = ByteArray(b.remaining()); b.get(bytes)
                            val packet = EncodedFrame(0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG, bytes).encode()
                            synchronized(codecConfigPackets) { codecConfigPackets.add(packet); if (codecConfigPackets.size > 2) codecConfigPackets.removeAt(0) }
                            channel.write(FrameTypes.ENCODED_FRAME, packet)
                        }
                    }
                }
                index >= 0 -> try {
                    encoder!!.getOutputBuffer(index)?.let { buffer ->
                        val bytes = ByteArray(info.size); buffer.position(info.offset); buffer.get(bytes)
                        channel.write(FrameTypes.ENCODED_FRAME, EncodedFrame(info.presentationTimeUs, info.flags, bytes).encode())
                    }
                } finally { runCatching { encoder?.releaseOutputBuffer(index, false) } }
            }
        }
    }
    private fun drainEncoderDiscard() { val info = MediaCodec.BufferInfo(); repeat(32) { val index = encoder?.dequeueOutputBuffer(info, 0) ?: return; if (index >= 0) runCatching { encoder?.releaseOutputBuffer(index, false) } } }
    private fun notification(): Notification { val channel = NotificationChannel("mirror", "Screen mirroring", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(channel); val stop = PendingIntent.getService(this, 8, Intent(this, MirroringService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return Notification.Builder(this, "mirror").setContentTitle(getString(com.carcast.mirror.R.string.app_name)).setContentText("Your screen is being shared locally").setSmallIcon(com.carcast.mirror.R.drawable.carcast_icon_monochrome).addAction(Notification.Action.Builder(null, "Stop", stop).build()).setOngoing(true).build() }
    override fun onDestroy() { running.set(false); worker?.interrupt(); display?.release(); projection?.stop(); encoder?.runCatching { stop(); release() }; inputSurface?.release(); socket?.close(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val ACTION_STOP = "com.carcast.mirror.STOP"; fun startIntent(context: Context, result: Int, data: Intent, host: String, port: Int, pin: String) = Intent(context, MirroringService::class.java).putExtra("result", result).putExtra("data", data).putExtra("host", host).putExtra("port", port).putExtra("pin", pin) }
}

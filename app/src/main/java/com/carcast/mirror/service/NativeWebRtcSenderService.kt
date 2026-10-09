package com.carcast.mirror.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.carcast.mirror.core.*
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

/** Native sender: existing MediaProjection/WebRTC capture and audio, TLS/SAS signaling only. */
class NativeWebRtcSenderService : Service() {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private var socket: SSLSocket? = null
    private var session: BrowserWebRtcSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (running.compareAndSet(false, true)) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            val data = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA) ?: run { stopSelf(); return START_NOT_STICKY }
            val result = intent.getIntExtra(EXTRA_PROJECTION_RESULT, Activity.RESULT_CANCELED)
            val host = intent.getStringExtra(EXTRA_HOST) ?: run { stopSelf(); return START_NOT_STICKY }
            val port = intent.getIntExtra(EXTRA_PORT, 0)
            val sas = intent.getStringExtra(EXTRA_SAS) ?: run { stopSelf(); return START_NOT_STICKY }
            val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "CarCast phone"
            val qualityMode = intent.getStringExtra(EXTRA_QUALITY_MODE)?.let { runCatching { BrowserQualityMode.valueOf(it) }.getOrNull() } ?: BrowserQualityMode.AUTO
            executor.execute { connect(host, port, sas, senderName, result, data, qualityMode) }
        }
        return START_NOT_STICKY
    }

    private fun connect(host: String, port: Int, sas: String, senderName: String, result: Int, data: Intent, qualityMode: BrowserQualityMode) {
        runCatching {
            socket = TlsIdentity.clientSocket(host, port).apply { soTimeout = 1_000; tcpNoDelay = true }
            val tls = socket!!
            require(TlsIdentity.sas(tls.session.peerCertificates.first()) == sas.filter(Char::isDigit)) { "Receiver verification code did not match" }
            val channel = TlsFramedChannel(DataInputStream(BufferedInputStream(tls.getInputStream())), DataOutputStream(BufferedOutputStream(tls.getOutputStream())))
            channel.write(FrameTypes.NATIVE_HELLO, JSONObject().put("senderName", senderName).toString().toByteArray())
            val approval = readFrame(channel, FrameTypes.NATIVE_APPROVED, FrameTypes.NATIVE_DECLINED) ?: error("Receiver approval connection closed")
            if (approval.type == FrameTypes.NATIVE_DECLINED) error(JSONObject(String(approval.payload)).optString("reason", "Receiver declined connection"))
            ProjectionLifecycle.begin("native-sender-${System.currentTimeMillis()}")
            session = BrowserWebRtcSession(this, { json -> channel.write(FrameTypes.NATIVE_SIGNAL, json.toByteArray()) }, audioEnabled = AppState.audio.value.enabled) { reason -> AppState.nativeReceiver { it.copy(lastDisconnectReason = reason) }; stopSelf() }
            ProjectionLifecycle.webRtcCreated()
            session!!.setQuality(qualityMode)
            ProjectionLifecycle.captureStarted()
            session!!.startCapture(result, data)
            while (running.get()) {
                val frame = readFrame(channel, FrameTypes.NATIVE_SIGNAL, FrameTypes.CLOSE) ?: break
                if (frame.type == FrameTypes.CLOSE) break
                session?.handle(JSONObject(String(frame.payload)))
            }
        }.onFailure { error ->
            AppState.nativeReceiver { it.copy(lastDisconnectReason = error.message, lastException = error.message) }
        }
        stopSelf()
    }

    private fun readFrame(channel: TlsFramedChannel, vararg expected: Int): TlsFramedChannel.Frame? {
        while (running.get()) {
            val frame = try { channel.read() } catch (_: java.net.SocketTimeoutException) { continue } catch (_: java.io.EOFException) { return null } catch (_: java.net.SocketException) { return null }
            if (frame.type in expected) return frame
        }
        return null
    }

    override fun onDestroy() {
        running.set(false)
        session?.stop(); session = null
        runCatching { socket?.close() }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val channel = NotificationChannel("native-cast", "Native casting", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val stop = PendingIntent.getService(this, NOTIFICATION_ID, Intent(this, NativeWebRtcSenderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "native-cast").setContentTitle(getString(com.carcast.mirror.R.string.app_name)).setContentText("Casting to CarCast Receiver").setSmallIcon(com.carcast.mirror.R.drawable.carcast_icon_monochrome).addAction(Notification.Action.Builder(null, "Stop", stop).build()).setOngoing(true).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_STOP = "com.carcast.mirror.native_sender.STOP"
        const val EXTRA_PROJECTION_RESULT = "projectionResult"
        const val EXTRA_PROJECTION_DATA = "projectionData"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_SAS = "sas"
        const val EXTRA_SENDER_NAME = "senderName"
        const val EXTRA_QUALITY_MODE = "qualityMode"
        const val NOTIFICATION_ID = 93
    }
}

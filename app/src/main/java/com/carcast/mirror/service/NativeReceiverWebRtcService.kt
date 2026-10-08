package com.carcast.mirror.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.carcast.mirror.core.*
import com.carcast.mirror.discovery.SERVICE_TYPE
import org.json.JSONObject
import org.webrtc.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Native receiver control plane. TLS/SAS handles signaling approval; WebRTC carries media. */
class NativeReceiverWebRtcService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var server: SSLServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var peer: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var egl: EglBase? = null
    private var socket: SSLSocket? = null
    private var channel: TlsFramedChannel? = null
    private val queuedCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_APPROVE) { approve(); return START_NOT_STICKY }
        if (intent?.action == ACTION_DECLINE) { decline(); return START_NOT_STICKY }
        if (running.compareAndSet(false, true)) startReceiver()
        return START_NOT_STICKY
    }

    private fun startReceiver() {
        NativeReceiverRuntime.enabled = true
        NativeReceiverRuntime.friendlyName = ReceiverIdentityStore.name(this)
        AppState.receiver { it.copy(active = true, status = "Starting", pairingCode = null, expiresAtMs = 0, friendlyName = NativeReceiverRuntime.friendlyName, pendingSender = null, pendingSas = null) }
        AppState.nativeReceiver { it.copy(discoveryState = "Starting", signalingState = "Listening") }
        executor.execute {
            runCatching {
                val identity = TlsIdentity.serverIdentity()
                server = TlsIdentity.serverSocket(identity).apply { soTimeout = 1000 }
                publish(identity.sas)
                AppState.nativeReceiver { it.copy(discoveryState = "Advertising", signalingState = "Listening") }
                while (running.get()) {
                    val accepted = try { server?.accept() as? SSLSocket } catch (_: SocketTimeoutException) { null } ?: continue
                    if (NativeReceiverRuntime.activeSender != null || NativeReceiverRuntime.pendingSender != null) {
                        runCatching { accepted.close() }
                        AppState.nativeReceiver { it.copy(lastDisconnectReason = "Receiver is currently in use") }
                        continue
                    }
                    handleConnection(accepted, identity.sas)
                }
            }.onFailure { error ->
                running.set(false)
                runCatching { server?.close() }
                server = null
                AppState.receiver { it.copy(active = false, status = "Failed", pairingCode = null, expiresAtMs = 0) }
                AppState.nativeReceiver { it.copy(signalingState = "Failed", lastException = "${error::class.java.simpleName}: ${error.message ?: "unknown receiver startup error"}") }
            }
        }
    }

    private fun publish(sas: String) {
        val nsd = getSystemService(NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = NativeReceiverRuntime.friendlyName
            serviceType = SERVICE_TYPE
            port = server!!.localPort
            setAttribute("protocol", "native-webrtc-v1")
            setAttribute("capabilities", "webrtc,avc,opus")
            setAttribute("session", UUID.randomUUID().toString())
        }
        registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { AppState.receiver { it.copy(port = info.port) } }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { AppState.nativeReceiver { it.copy(discoveryState = "Failed") } }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        AppState.receiver { it.copy(status = "Ready to receive", pairingCode = sas, expiresAtMs = Long.MAX_VALUE, port = server!!.localPort) }
    }

    private fun handleConnection(accepted: SSLSocket, sas: String) {
        socket = accepted
        accepted.soTimeout = 1_000
        accepted.startHandshake()
        val input = DataInputStream(BufferedInputStream(accepted.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(accepted.getOutputStream()))
        channel = TlsFramedChannel(input, output)
        val hello = readUntilFrame(FrameTypes.NATIVE_HELLO) ?: return
        val sender = JSONObject(hello.toString(Charsets.UTF_8)).optString("senderName", "CarCast phone").take(40)
        NativeReceiverRuntime.beginApproval(sender, sas)
        AppState.receiver { it.copy(status = "Approval required", pendingSender = sender, pendingSas = sas) }
        AppState.nativeReceiver { it.copy(signalingState = "Approval required") }
        if (!NativeReceiverRuntime.awaitDecision(60_000)) {
            runCatching { channel?.write(FrameTypes.NATIVE_DECLINED, JSONObject().put("reason", "Receiver approval timed out").toString().toByteArray()) }
            closeConnection("Approval timed out")
            return
        }
        channel?.write(FrameTypes.NATIVE_APPROVED, JSONObject().put("receiverName", NativeReceiverRuntime.friendlyName).toString().toByteArray())
        NativeReceiverRuntime.activeSender = sender
        AppState.receiver { it.copy(status = "Connecting", pendingSender = null, pendingSas = null) }
        AppState.nativeReceiver { it.copy(signalingState = "Approved", webRtcState = "Connecting") }
        receiveWebRtc(sender)
    }

    private fun receiveWebRtc(sender: String) {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).setEnableInternalTracer(false).createInitializationOptions())
        egl = EglBase.create()
        val builder = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl!!.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl!!.eglBaseContext, true, true))
        factory = builder.createPeerConnectionFactory()
        peer = factory!!.createPeerConnection(emptyList(), observer()) ?: error("Native receiver PeerConnection unavailable")
        try {
            while (running.get() && NativeReceiverRuntime.activeSender == sender) {
                val frame = readUntilFrame(FrameTypes.NATIVE_SIGNAL) ?: break
                handleSignal(JSONObject(frame.toString(Charsets.UTF_8)))
            }
        } catch (error: Throwable) {
            AppState.nativeReceiver { it.copy(lastException = error.message, lastDisconnectReason = "Signaling closed") }
        } finally {
            closePeer()
            NativeReceiverRuntime.activeSender = null
            NativeReceiverRuntime.clearApproval()
            if (running.get()) AppState.receiver { it.copy(status = "Ready to receive") }
        }
    }

    private fun handleSignal(json: JSONObject) {
        when (json.optString("type")) {
            "offer" -> {
                val description = SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp"))
                peer!!.setRemoteDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        synchronized(queuedCandidates) { queuedCandidates.forEach(peer!!::addIceCandidate); queuedCandidates.clear() }
                        peer!!.createAnswer(object : SdpObserverAdapter() {
                            override fun onCreateSuccess(answer: SessionDescription?) {
                                if (answer == null) return
                                peer!!.setLocalDescription(object : SdpObserverAdapter() { override fun onSetSuccess() { sendSignal(JSONObject().put("type", "answer").put("sdp", answer.description)) } }, answer)
                            }
                        }, MediaConstraints())
                    }
                }, description)
            }
            "candidate" -> {
                val candidate = IceCandidate(json.optString("sdpMid"), json.optInt("sdpMLineIndex"), json.getString("candidate"))
                if (remoteDescriptionSet) peer?.addIceCandidate(candidate) else synchronized(queuedCandidates) { queuedCandidates += candidate }
            }
        }
    }

    private fun observer() = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) { sendSignal(JSONObject().put("type", "candidate").put("candidate", candidate.sdp).put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex)) }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) { AppState.nativeReceiver { it.copy(webRtcState = state.name) }; if (state == PeerConnection.PeerConnectionState.CONNECTED) AppState.receiver { it.copy(status = "Connected") }; if (state == PeerConnection.PeerConnectionState.FAILED) AppState.nativeReceiver { it.copy(lastDisconnectReason = "WebRTC connection failed") } }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { AppState.nativeReceiver { it.copy(iceState = state.name) } }
        override fun onTrack(transceiver: RtpTransceiver) {
            when (val track = transceiver.receiver.track()) {
                is VideoTrack -> { NativeReceiverRenderer.setTrack(track); AppState.nativeReceiver { it.copy(videoCodec = "WebRTC video", signalingState = "Media received") } }
                is AudioTrack -> { track.setEnabled(true); AppState.nativeReceiver { it.copy(audioTrackStatus = "Connected", audioCodec = "Opus") } }
            }
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState) { AppState.nativeReceiver { it.copy(signalingState = state.name) } }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {}
    }

    private fun sendSignal(json: JSONObject) { runCatching { channel?.write(FrameTypes.NATIVE_SIGNAL, json.toString().toByteArray()) }.onFailure { error -> AppState.nativeReceiver { it.copy(lastException = error.message) } } }

    private fun readUntilFrame(expected: Int): ByteArray? {
        while (running.get()) {
            val frame = try { channel?.read() } catch (_: java.net.SocketTimeoutException) { continue } ?: return null
            if (frame.type == expected) return frame.payload
            if (frame.type == FrameTypes.CLOSE) return null
        }
        return null
    }

    fun approve() { NativeReceiverRuntime.decide(true) }
    fun decline() { NativeReceiverRuntime.decide(false) }

    private fun closeConnection(reason: String) { AppState.nativeReceiver { it.copy(lastDisconnectReason = reason) }; runCatching { socket?.close() }; closePeer(); NativeReceiverRuntime.clearApproval() }
    private fun closePeer() { runCatching { peer?.close() }; peer = null; runCatching { factory?.dispose() }; factory = null; runCatching { egl?.release() }; egl = null; NativeReceiverRenderer.clearTrack() }

    override fun onDestroy() {
        running.set(false)
        NativeReceiverRuntime.reset()
        closeConnection("Receiver stopped")
        val nsd = getSystemService(NSD_SERVICE) as NsdManager
        registration?.let { runCatching { nsd.unregisterService(it) } }
        runCatching { server?.close() }
        executor.shutdownNow()
        AppState.receiver { it.copy(active = false, status = "Stopped", pairingCode = null, expiresAtMs = 0, pendingSender = null, pendingSas = null) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_STOP = "com.carcast.mirror.native_receiver.STOP"
        const val ACTION_APPROVE = "com.carcast.mirror.native_receiver.APPROVE"
        const val ACTION_DECLINE = "com.carcast.mirror.native_receiver.DECLINE"
    }
}

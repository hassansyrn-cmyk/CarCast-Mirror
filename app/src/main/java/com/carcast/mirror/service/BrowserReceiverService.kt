package com.carcast.mirror.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.carcast.mirror.core.*
import com.carcast.mirror.core.BrowserSessionPolicy
import com.carcast.mirror.core.BrowserStatus
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BrowserReceiverService : Service() {
    private val executor = Executors.newCachedThreadPool()
    private val starting = AtomicBoolean(false)
    private val captureStartClaimed = AtomicBoolean(false)
    private var browserSessionId: String = ""
    private var intentionalStop = false
    private var http: ServerSocket? = null
    private var signal: SignalServer? = null
    private var session: BrowserWebRtcSession? = null
    private var token = ""
    private var policy: BrowserSessionPolicy? = null
    private var requestedQuality = BrowserQualityMode.AUTO
    private var requestedAudio = true
    private var lanAddress = ""
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() { super.onCreate(); requestedAudio = AppState.audio.value.enabled; AppState.resetBrowserReceiverState(); DebugDiagnostics.beginSession(); browserSessionId = java.util.UUID.randomUUID().toString(); ProjectionLifecycle.begin(browserSessionId); AppState.browser { it.copy(sessionId = browserSessionId) }; registerNetworkCallback(); DebugDiagnostics.stage("BROWSER_SESSION_CREATED:$browserSessionId"); DebugDiagnostics.stage(STAGE_FOREGROUND_SERVICE_CREATED) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { intentionalStop = true; captureStartClaimed.set(false); session?.stop(); session = null; stopSelf(); return START_NOT_STICKY }
            ACTION_REJECT -> { signal?.reject(); return START_NOT_STICKY }
            ACTION_SET_QUALITY -> {
                runCatching { requestedQuality = BrowserQualityMode.valueOf(intent.getStringExtra(EXTRA_QUALITY) ?: BrowserQualityMode.AUTO.name); session?.setQuality(requestedQuality) }
                return START_NOT_STICKY
            }
            ACTION_SET_AUDIO -> { requestedAudio = intent.getBooleanExtra(EXTRA_AUDIO, true); session?.setAudioEnabled(requestedAudio); return START_NOT_STICKY }
        }
        if (intent?.hasExtra("projectionResult") == true) {
            ProjectionLifecycle.approveCommand()
            val data = intent.getParcelableExtra<Intent>("projectionData")
            if (data != null) signal?.approve(intent.getIntExtra("projectionResult", -1), data, intent.getStringExtra(EXTRA_SESSION_ID) ?: browserSessionId)
        }
        if (starting.compareAndSet(false, true)) {
            AppState.browser { it.copy(status = BrowserStatus.STARTING, error = null) }
            executor.execute { startServers() }
        }
        return START_NOT_STICKY
    }

    private fun reservePort(): Int = ServerSocket(0).use { it.localPort }

    private fun startServers() {
        runCatching {
            policy = BrowserSessionPolicy()
            token = policy!!.token
            http = ServerSocket(0)
            AppState.browser { it.copy(status = BrowserStatus.STARTING, error = null) }
            var lastFailure: Throwable? = null
            repeat(3) {
                if (signal != null) return@repeat
                val port = reservePort()
                val latch = CountDownLatch(1)
                val startupError = AtomicReference<Throwable?>(null)
                val candidate = SignalServer(port, latch, startupError)
                try {
                    candidate.start()
                    if (!latch.await(5, TimeUnit.SECONDS)) throw IllegalStateException("WebSocket signaling server startup timed out")
                    startupError.get()?.let { throw it }
                    require(candidate.getPort() > 0) { "WebSocket signaling server has no bound port" }
                    signal = candidate
                } catch (failure: Throwable) {
                    lastFailure = failure
                    runCatching { candidate.stop(1000) }
                }
            }
            if (signal == null) throw IllegalStateException("WebSocket signaling server failed to bind", lastFailure)
            val address = reachableLanAddress()
            lanAddress = address
            require(http!!.isBound && !http!!.isClosed) { "HTTP server failed to bind" }
            BrowserReceiverState.start(address, http!!.localPort, signal!!.getPort(), token)
            AppState.browser { it.copy(status = BrowserStatus.WAITING_FOR_BROWSER, address = address, httpPort = http!!.localPort, signalPort = signal!!.getPort(), error = null) }
            while (!Thread.currentThread().isInterrupted) {
                http!!.accept().use { client ->
                    val body = page(); val bytes = body.toByteArray()
                    client.getOutputStream().bufferedWriter().use { out ->
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n$body"); out.flush()
                    }
                }
            }
        }.onFailure { ex ->
            AppState.failBrowserReceiver(ex.message ?: "Browser Receiver failed to start")
            starting.set(false)
            stopSelf()
        }
    }

    private inner class SignalServer(port: Int, private val ready: CountDownLatch, private val startupError: AtomicReference<Throwable?>) : WebSocketServer(InetSocketAddress(port)) {
        private var approved = false
        private var browser: WebSocket? = null

        override fun onStart() { ready.countDown() }
        override fun onError(conn: WebSocket?, ex: Exception) { startupError.compareAndSet(null, ex); AppState.browser { it.copy(status = BrowserStatus.FAILED, error = ex.message ?: "WebSocket signaling server failed") } }
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            val query = handshake.resourceDescriptor.substringAfter("?", "").split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            if (query["token"] != token || policy?.authorize(conn.remoteSocketAddress.toString()) != true) { conn.close(1008, "invalid or expired session"); return }
            browser?.takeIf { it != conn }?.let { runCatching { it.close(1000, "replaced by newer browser connection") } }
            browser = conn
            DebugDiagnostics.stage(STAGE_BROWSER_CONNECTED)
            DebugDiagnostics.stage(STAGE_BROWSER_APPROVAL_REQUIRED)
            AppState.browser { it.copy(status = BrowserStatus.APPROVAL_REQUIRED, remoteAddress = conn.remoteSocketAddress.address.hostAddress, sessionStartedAtMs = System.currentTimeMillis()) }
            conn.send(JSONObject().put("type", "approval-required").toString())
        }
        override fun onMessage(conn: WebSocket, message: String) { runCatching { val json = JSONObject(message); when (json.optString("type")) { "browser-info" -> AppState.browser { it.copy(browserUserAgent = json.optString("userAgent")) }; "answer", "candidate" -> session?.handle(json) } }.onFailure { DebugDiagnostics.error("SIGNALING_MESSAGE", it); runCatching { conn.close(1011, "bad signaling") } } }
        fun approve(resultCode: Int, data: Intent, sessionId: String) {
            if (ProjectionLifecycle.snapshot().contains("startCapture=1")) ProjectionLifecycle.duplicate("approval")
            val conn = browser ?: run { AppState.failBrowserReceiver("SIGNALING LOST DURING APPROVAL"); return }
            if (sessionId != browserSessionId || !captureStartClaimed.compareAndSet(false, true)) { DebugDiagnostics.error("DUPLICATE_MEDIA_PROJECTION_USE_PREVENTED", IllegalStateException("Duplicate approval ignored for session $browserSessionId")); conn.send(JSONObject().put("type", "approval-ignored").put("reason", "Duplicate MediaProjection use prevented").put("sessionId", browserSessionId).toString()); return }
            if (policy?.approve(true) != true) { conn.close(1008, "expired session"); AppState.failBrowserReceiver("SIGNALING SESSION EXPIRED"); return }
            try {
                approved = true
                ServiceCompat.startForeground(this@BrowserReceiverService, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                DebugDiagnostics.stage(STAGE_START_FOREGROUND_OK)
                AppState.browser { it.copy(status = BrowserStatus.NEGOTIATING) }
                session?.stop()
                // ScreenCapturerAndroid is the sole consumer of the single-use consent Intent.
                // Do not call MediaProjectionManager.getMediaProjection here.
                session = BrowserWebRtcSession(this@BrowserReceiverService, { text -> if (conn.isOpen) conn.send(text) }, requestedAudio) { reason -> session?.stop(); session = null; approved = false; starting.set(false); runCatching { conn.close(1011, reason) }; stopSelf() }
                DebugDiagnostics.stage(STAGE_WEBRTC_SESSION_CREATED)
                ProjectionLifecycle.webRtcCreated()
                session!!.setQuality(requestedQuality)
                ProjectionLifecycle.captureStarted()
                session!!.startCapture(resultCode, data)
            } catch (t: Throwable) {
                DebugDiagnostics.error(STAGE_SCREEN_CAPTURE_STARTED, t)
                session?.stop(); session = null; approved = false; starting.set(false)
                AppState.failBrowserReceiver("UNABLE TO START SCREEN SHARING: ${t.message ?: t::class.java.simpleName}")
                runCatching { conn.close(1011, "screen sharing failed") }
                stopSelf()
            }
        }
        fun reject() { policy?.approve(false); captureStartClaimed.set(false); browser?.close(1008, "rejected by phone"); intentionalStop = true; stopSelf() }
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            if (conn != browser) return
            browser = null
            if (approved || session != null) failAndStop("TV disconnected — tap Reconnect")
            else AppState.browser { it.copy(status = BrowserStatus.WAITING_FOR_BROWSER, remoteAddress = null, browserUserAgent = null, sessionStartedAtMs = null) }
        }
    }

    private fun page() = """<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><title>CarCast Browser Receiver</title><style>html,body{margin:0;background:#071014;color:#f2f7f8;font:20px system-ui;text-align:center}main{padding:8vh 6vw}video{width:100%;height:78vh;max-height:78vh;background:#000;object-fit:contain}button{font:inherit;padding:.7em 1em;margin:.6em}#o{position:fixed;top:1rem;right:1rem;background:#071014cc;padding:.5rem .8rem;border-radius:.5rem;opacity:0;transition:opacity .5s}</style><main><h1>CarCast Mirror</h1><p id=s>Checking WebRTC support…</p><video id=v autoplay playsinline></video><div id=o>CarCast · Connected</div><div id=a>Audio track: Missing · Muted: No · Audio playback: Waiting</div><button id=f>Fullscreen</button><button id=p style="display:none">Press OK / Play to enable audio</button></main><script>(async()=>{const s=document.getElementById('s'),v=document.getElementById('v'),o=document.getElementById('o'),f=document.getElementById('f'),a=document.getElementById('a'),p=document.getElementById('p');v.muted=false;if(!window.RTCPeerConnection){s.textContent='This browser does not support WebRTC.';return}const token='$token',ws=new WebSocket('ws://${BrowserReceiverState.address}:${BrowserReceiverState.signalPort}/signal?token='+encodeURIComponent(token));let pc,remoteDescriptionSet=false,queued=[];const log=x=>{console.info('CarCast',x);s.textContent=x;if(x.includes('Connected')){o.style.opacity=1;setTimeout(()=>{o.style.opacity=0;f.style.display='none';s.style.display='none'},4000)}};ws.onopen=()=>{log('Waiting for phone approval');ws.send(JSON.stringify({type:'browser-info',userAgent:navigator.userAgent}))};ws.onmessage=async e=>{const m=JSON.parse(e.data);if(m.type==='approval-required'){log('Approval required on phone')}if(m.type==='candidate'){if(!pc||!remoteDescriptionSet)queued.push(m);else await pc.addIceCandidate({candidate:m.candidate,sdpMid:m.sdpMid,sdpMLineIndex:m.sdpMLineIndex});console.info('CarCast remote candidate')}if(m.type==='offer'){pc=new RTCPeerConnection({iceServers:[]});pc.ontrack=e=>{const stream=e.streams[0],videoTracks=stream.getVideoTracks(),audioTracks=stream.getAudioTracks();v.srcObject=stream;a.textContent='Tracks: video '+videoTracks.length+' · audio '+audioTracks.length+' · Muted: '+(v.muted?'Yes':'No')+' · Audio playback: Starting';if(videoTracks.length!==1||audioTracks.length!==1){log('Track validation failed — video '+videoTracks.length+', audio '+audioTracks.length)};v.play().then(()=>{a.textContent=a.textContent.replace('Starting','Playing')}).catch(()=>{a.textContent=a.textContent.replace('Starting','Blocked');p.style.display='inline-block';log('Press OK / Play to enable audio')});log('Connected — live phone screen')};pc.onicecandidate=e=>{if(e.candidate){console.info('CarCast local candidate');ws.send(JSON.stringify({type:'candidate',candidate:e.candidate.candidate,sdpMid:e.candidate.sdpMid,sdpMLineIndex:e.candidate.sdpMLineIndex}))}};pc.onconnectionstatechange=()=>{console.info('CarCast PeerConnection state',pc.connectionState);if(['failed','disconnected'].includes(pc.connectionState))log('Connection '+pc.connectionState)};pc.oniceconnectionstatechange=()=>console.info('CarCast ICE state',pc.iceConnectionState);await pc.setRemoteDescription({type:'offer',sdp:m.sdp});remoteDescriptionSet=true;for(const c of queued)await pc.addIceCandidate({candidate:c.candidate,sdpMid:c.sdpMid,sdpMLineIndex:c.sdpMLineIndex});queued=[];const a=await pc.createAnswer();await pc.setLocalDescription(a);console.info('CarCast answer sent');ws.send(JSON.stringify({type:'answer',sdp:a.sdp}))}};f.onclick=()=>v.requestFullscreen?.();p.onclick=()=>v.play().then(()=>{p.style.display='none';log('Connected — live phone screen')}).catch(()=>{})})()</script>"""

    private fun reachableLanAddress(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let(cm::getNetworkCapabilities)
        require(caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) { "active network is not a reachable local LAN" }
        return network?.let(cm::getLinkProperties)?.linkAddresses?.mapNotNull { link -> link.address.hostAddress?.takeIf { !link.address.isLoopbackAddress && !it.contains(':') } }?.firstOrNull() ?: throw IllegalStateException("No reachable local IPv4 network")
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { if (!intentionalStop && starting.get()) failAndStop("Wi-Fi connection changed — tap Reconnect") }
            override fun onAvailable(network: Network) {
                if (intentionalStop || !starting.get()) return
                val current = runCatching { reachableLanAddress() }.getOrNull() ?: return
                if (lanAddress.isNotBlank() && current != lanAddress) failAndStop("Wi-Fi connection changed — tap Reconnect")
            }
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }.onFailure { DebugDiagnostics.error("NETWORK_MONITOR", it) }
    }

    private fun failAndStop(message: String) {
        if (intentionalStop) return
        DebugDiagnostics.error("RECOVERY_STOP", IllegalStateException(message))
        AppState.failBrowserReceiver(message)
        starting.set(false)
        session?.stop(); session = null
        runCatching { signal?.stop(1000) }; signal = null
        runCatching { http?.close() }; http = null
        stopSelf()
    }

    override fun onDestroy() {
        session?.stop(); session = null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
        captureStartClaimed.set(false)
        runCatching { signal?.stop(1000) }; signal = null
        runCatching { http?.close() }; http = null
        token = ""; policy = null; BrowserReceiverState.stop()
        executor.shutdownNow(); executor.awaitTermination(2, TimeUnit.SECONDS)
        if (intentionalStop) AppState.resetBrowserReceiverState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    private fun notification(): Notification { val channel = NotificationChannel("browser-mirror", "Browser mirroring", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(channel); val stop = PendingIntent.getService(this, 91, Intent(this, BrowserReceiverService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return Notification.Builder(this, "browser-mirror").setContentTitle(getString(com.carcast.mirror.R.string.app_name)).setContentText("Sharing screen with TV browser").setSmallIcon(com.carcast.mirror.R.drawable.carcast_icon_monochrome).addAction(Notification.Action.Builder(null, "Stop", stop).build()).setOngoing(true).build() }
    companion object { const val ACTION_STOP = "com.carcast.mirror.BROWSER_STOP"; const val ACTION_REJECT = "com.carcast.mirror.BROWSER_REJECT"; const val ACTION_SET_QUALITY = "com.carcast.mirror.BROWSER_SET_QUALITY"; const val EXTRA_QUALITY = "quality"; const val ACTION_SET_AUDIO = "com.carcast.mirror.BROWSER_SET_AUDIO"; const val EXTRA_AUDIO = "audio"; const val EXTRA_SESSION_ID = "sessionId"; const val NOTIFICATION_ID = 91 }
}

object BrowserReceiverState { @Volatile var active = false; @Volatile var address = ""; @Volatile var port = 0; @Volatile var signalPort = 0; @Volatile var token = ""; fun start(host: String, p: Int, signal: Int, sessionToken: String) { active = true; address = host; port = p; signalPort = signal; token = sessionToken }; fun stop() { active = false; address = ""; port = 0; signalPort = 0; token = "" } }

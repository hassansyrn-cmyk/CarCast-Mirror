package com.carcast.mirror

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.view.SurfaceView
import com.carcast.mirror.discovery.*
import com.carcast.mirror.service.*
import com.carcast.mirror.core.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    private fun localNetworkGranted() = Build.VERSION.SDK_INT < 37 || checkSelfPermission(localNetworkPermission) == PackageManager.PERMISSION_GRANTED
    private fun requestLocalNetwork() { if (Build.VERSION.SDK_INT >= 37) requestPermissions(arrayOf(localNetworkPermission), 42) }
    private var selected: DiscoveredReceiver? = null
    private var selectedPin = ""
    private var browserApproval = false
    private var audioPermissionRequested = false
    private val projection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if (result.resultCode == Activity.RESULT_OK && result.data != null && selected != null) startForegroundService(MirroringService.startIntent(this, result.resultCode, result.data!!, selected!!.host.hostAddress!!, selected!!.port, selectedPin)) }
    private val browserProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ProjectionLifecycle.consentResult()
            DebugDiagnostics.stage(STAGE_PROJECTION_RESULT_OK)
            try {
                DebugDiagnostics.stage(STAGE_FOREGROUND_SERVICE_START_REQUESTED)
                ProjectionLifecycle.approveIntent()
                ContextCompat.startForegroundService(this, Intent(this, BrowserReceiverService::class.java).putExtra("projectionResult", result.resultCode).putExtra("projectionData", result.data).putExtra(BrowserReceiverService.EXTRA_SESSION_ID, AppState.browser.value.sessionId))
            } catch (t: Throwable) {
                DebugDiagnostics.error(STAGE_FOREGROUND_SERVICE_START_REQUESTED, t)
                AppState.failBrowserReceiver("FOREGROUND SERVICE START FAILED: ${t.message ?: t::class.java.simpleName}")
            }
        } else {
            DebugDiagnostics.stage(STAGE_PROJECTION_RESULT_CANCELLED)
            AppState.failBrowserReceiver("UNABLE TO START MEDIAPROJECTION: consent was cancelled")
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DebugDiagnostics.init(this)
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugDiagnostics.uncaught(throwable)
            prior?.uncaughtException(thread, throwable)
        }
        setContent { CarCastApp() }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == 42) recreate(); if (requestCode == 43) { if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) AppState.audio { it.copy(status = "Audio: Initializing") } else AppState.audio { it.copy(status = "Audio: Unavailable — permission denied; video only") }; launchBrowserProjection() } }
    private fun launchBrowserProjection() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && AppState.audio.value.enabled && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && !audioPermissionRequested) { audioPermissionRequested = true; AppState.audio { it.copy(status = "Audio: Waiting for permission") }; requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 43); return }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && AppState.audio.value.enabled && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) AppState.audio { it.copy(status = "Audio: Unavailable — permission denied; video only") }
            DebugDiagnostics.stage(STAGE_BROWSER_APPROVE_CLICKED)
            DebugDiagnostics.stage(STAGE_PROJECTION_CONSENT_LAUNCHED)
            browserProjection.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
        } catch (t: Throwable) {
            DebugDiagnostics.error(STAGE_PROJECTION_CONSENT_LAUNCHED, t)
            AppState.failBrowserReceiver("UNABLE TO START MEDIAPROJECTION: ${t.message ?: t::class.java.simpleName}")
        }
    }
    @Composable private fun CarCastApp() {
        var mode by remember { mutableStateOf("cast") }; var localGranted by remember { mutableStateOf(localNetworkGranted()) }; var scanning by remember { mutableStateOf(false) }; var receivers by remember { mutableStateOf(listOf<DiscoveredReceiver>()) }; var showPin by remember { mutableStateOf<DiscoveredReceiver?>(null) }; var pin by remember { mutableStateOf("") }; var manual by remember { mutableStateOf(false) }; var qualityOpen by remember { mutableStateOf(false) }; val browserState by AppState.browser.collectAsStateWithLifecycle(); val receiverState by AppState.receiver.collectAsStateWithLifecycle(); val diagnostics by AppState.diagnostics.collectAsStateWithLifecycle(); val debug by AppState.debug.collectAsStateWithLifecycle(); val audio by AppState.audio.collectAsStateWithLifecycle()
        val discovery = remember { DiscoveryCoordinator(this) }
        DisposableEffect(mode, localGranted) { if (mode == "cast" && localGranted) { scanning = true; discovery.start { receivers = it } }; onDispose { discovery.stop() } }
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF35D7C4), background = Color(0xFF071014), surface = Color(0xFF101C22))) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF071014)) { Column((Modifier.fillMaxSize().padding(22.dp)).then(if (mode == "cast") Modifier else Modifier.verticalScroll(rememberScrollState()))) {
                Text("CarCast Mirror", style = MaterialTheme.typography.headlineMedium, color = Color.White); Text("SECURE LOCAL CASTING", style = MaterialTheme.typography.labelSmall, color = Color(0xFF35D7C4)); Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("cast" to "Cast screen", "receive" to "Receive", "browser" to "Browser", "car" to "Car mode", "diag" to "Diagnostics").forEach { (id, label) -> FilterChip(selected = mode == id, onClick = { mode = id }, label = { Text(label) }) } }
                Spacer(Modifier.height(18.dp))
                when (mode) {
                    "cast" -> { Text("Available displays", style = MaterialTheme.typography.headlineSmall, color = Color.White); Text("Unified discovery: CarCast, Miracast handoff, and media-only targets.", color = Color(0xFF8FA3A9)); Spacer(Modifier.height(12.dp)); if (!localGranted) { Text("CarCast needs local network access to discover and securely connect to a receiver. Screen content is not uploaded.", color = Color(0xFF8FA3A9)); OutlinedButton(onClick = { requestLocalNetwork() }, modifier = Modifier.fillMaxWidth()) { Text("Allow local network access") } } else { LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(receivers.size) { i -> val target = receivers[i]; OutlinedButton(enabled = target.protocol == "CarCast Native", onClick = { showPin = target }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(target.name, color = Color.White); Text("${target.protocol} · ${target.host.hostAddress}:${target.port}", color = Color(0xFF8FA3A9)); Text(if (target.protocol == "DLNA") "MEDIA ONLY — not full-screen mirroring" else "Pairing code is shown on the receiver", color = if (target.protocol == "DLNA") Color(0xFFFFC857) else Color(0xFF35D7C4)) } } } }; OutlinedButton(onClick = { if (localGranted) { scanning = true; discovery.start { receivers = it } } else requestLocalNetwork() }, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Scan again" else "Scan for displays") }; OutlinedButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth()) { Text("Enter IP, port, and pairing code") }; OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_CAST_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Android System Cast / Miracast") } } }
                    "receive" -> { Text("CarCast Receiver", style = MaterialTheme.typography.headlineSmall, color = Color.White); Text(receiverState.status, color = Color(0xFF8FA3A9)); Text("Pairing code: ${receiverState.pairingCode?.chunked(3)?.joinToString(" ") ?: "Start receiver"}", color = Color.White, style = MaterialTheme.typography.titleLarge); Text("SAS valid for this receiver TLS session", color = Color(0xFF35D7C4)); Spacer(Modifier.height(12.dp)); AndroidView(factory = { context -> SurfaceView(context).also { ReceiverSurfaceHolder.attach(it.holder) } }, modifier = Modifier.fillMaxWidth().height(220.dp)); Spacer(Modifier.height(14.dp)); Button(onClick = { if (localGranted) startService(Intent(this@MainActivity, ReceiverService::class.java)) else requestLocalNetwork() }, modifier = Modifier.fillMaxWidth()) { Text("Start receiver") } }
                    "browser" -> { Text("Browser Receiver", style = MaterialTheme.typography.headlineSmall, color = Color.White); when { browserState.status == BrowserStatus.STARTING -> { Text("Starting Browser Receiver…", color = Color(0xFF35D7C4)); Button(enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Starting…") } }; browserState.status == BrowserStatus.FAILED -> { Text("Browser Receiver failed to start", color = Color(0xFFFF6B6B)); Text(browserState.error ?: "Unknown startup error", color = Color(0xFFFFB4AB)); Button(onClick = { startService(Intent(this@MainActivity, BrowserReceiverService::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("Retry") } }; browserState.status in setOf(BrowserStatus.WAITING_FOR_BROWSER, BrowserStatus.APPROVAL_REQUIRED, BrowserStatus.NEGOTIATING, BrowserStatus.CONNECTED) -> { Text(browserState.status.name.replace('_', ' '), color = Color(0xFF35D7C4)); Box { OutlinedButton(onClick = { qualityOpen = true }) { Text("Selected profile: ${browserState.qualityMode.name.replace('_', ' ')}") }; DropdownMenu(expanded = qualityOpen, onDismissRequest = { qualityOpen = false }) { BrowserQualityMode.values().forEach { mode -> DropdownMenuItem(text = { Text(mode.name.replace('_', ' ')) }, onClick = { qualityOpen = false; startService(Intent(this@MainActivity, BrowserReceiverService::class.java).setAction(BrowserReceiverService.ACTION_SET_QUALITY).putExtra(BrowserReceiverService.EXTRA_QUALITY, mode.name)); AppState.browser { it.copy(qualityMode = mode) } }) } } }; Text("Open on your TV:", color = Color.White); Text("http://${browserState.address}:${browserState.httpPort}", color = Color(0xFF35D7C4)); val qr = remember(browserState.address, browserState.httpPort) { qrBitmap("http://${browserState.address}:${browserState.httpPort}") }; Image(qr.asImageBitmap(), "Browser receiver QR", Modifier.size(180.dp)); Text("Browser: ${browserState.browserUserAgent ?: "Waiting for browser"}", color = Color(0xFF8FA3A9)); Text("LAN address: ${browserState.remoteAddress ?: "Waiting for browser"}", color = Color(0xFF8FA3A9)); Text("Actual stream: ${diagnostics.resolution} · ${diagnostics.fps?.let { "%.1f FPS".format(it) } ?: "FPS unavailable"} · ${diagnostics.bitrateKbps?.let { "$it kbps" } ?: "bitrate unavailable"}", color = Color(0xFF35D7C4)); diagnostics.qualityNote?.let { Text(it, color = Color(0xFFFFC857)) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Share device audio", color = Color.White); Switch(checked = audio.enabled, onCheckedChange = { value -> AppState.audio { it.copy(enabled = value, status = if (value) "Audio: Waiting for permission" else "Audio: Disabled") }; if (browserState.status != BrowserStatus.STOPPED) startService(Intent(this@MainActivity, BrowserReceiverService::class.java).setAction(BrowserReceiverService.ACTION_SET_AUDIO).putExtra(BrowserReceiverService.EXTRA_AUDIO, value)) }, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) }; Text("Audio: ${audio.status}", color = Color(0xFFFFC857)); if (browserState.status == BrowserStatus.APPROVAL_REQUIRED) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { launchBrowserProjection() }, modifier = Modifier.weight(1f)) { Text("Approve") }; OutlinedButton(onClick = { startService(Intent(this@MainActivity, BrowserReceiverService::class.java).setAction(BrowserReceiverService.ACTION_REJECT)) }, modifier = Modifier.weight(1f)) { Text("Reject") } } }; OutlinedButton(onClick = { startService(Intent(this@MainActivity, BrowserReceiverService::class.java).setAction(BrowserReceiverService.ACTION_STOP)) }, modifier = Modifier.fillMaxWidth()) { Text("Stop Receiver") } }; else -> { Text("Start a temporary local receiver page with WebRTC signaling.", color = Color(0xFF8FA3A9)); Button(onClick = { if (localGranted) startService(Intent(this@MainActivity, BrowserReceiverService::class.java)) else requestLocalNetwork() }, modifier = Modifier.fillMaxWidth()) { Text("Start Browser Receiver") } } } }
                    "car" -> { Text("Car mode", style = MaterialTheme.typography.headlineSmall, color = Color.White); Spacer(Modifier.height(8.dp)); Text("Factory Android Auto displays are not generic screen receivers. CarCast cannot bypass Android Auto or inject arbitrary phone pixels into a Nissan Rogue factory display.", color = Color(0xFF8FA3A9)); Spacer(Modifier.height(14.dp)); Text("Supported paths", style = MaterialTheme.typography.titleMedium, color = Color.White); Text("• Android-powered head unit with CarCast Receiver installed directly.\n• Android Automotive OS only through an eligible, reviewed parked-app category.\n• External Miracast or receiver hardware using Android's public system handoff.", color = Color(0xFF8FA3A9)) }
                    else -> { Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, color = Color.White); Text("Live session metrics", color = Color(0xFF8FA3A9)); Spacer(Modifier.height(12.dp)); listOf("Protocol" to diagnostics.protocol, "State" to diagnostics.state, "Receiver" to diagnostics.receiver, "Codec" to diagnostics.codec, "Capture resolution" to diagnostics.captureResolution, "Sent resolution" to diagnostics.resolution, "FPS" to (diagnostics.fps?.toString() ?: "Unavailable"), "Encoded FPS" to (diagnostics.encodedFps?.toString() ?: "Unavailable"), "Bitrate" to (diagnostics.bitrateKbps?.let { "$it kbps" } ?: "Unavailable"), "Bytes/sec" to (diagnostics.bytesPerSecond?.toString() ?: "Unavailable"), "Frames sent" to (diagnostics.framesSent?.toString() ?: "Unavailable"), "ICE state" to (diagnostics.iceState ?: "Unavailable"), "Selected candidate pair" to (diagnostics.selectedCandidatePair ?: "Unavailable"), "Local candidate type" to (diagnostics.localCandidateType ?: "Unavailable"), "Remote candidate type" to (diagnostics.remoteCandidateType ?: "Unavailable"), "RTT" to (diagnostics.rttMs?.let { "$it ms" } ?: "Unavailable"), "Packets lost" to (diagnostics.packetsLost?.toString() ?: "Unavailable"), "Available outgoing bitrate" to (diagnostics.availableOutgoingBitrateKbps?.let { "$it kbps" } ?: "Unavailable"), "Quality limitation" to (diagnostics.qualityLimitationReason ?: "None reported"), "Audio track" to (diagnostics.audioTrackState ?: "Missing"), "Audio source" to (diagnostics.audioCaptureSource ?: "Unavailable"), "Audio codec" to (diagnostics.audioCodec ?: "Unavailable"), "Audio sample rate" to (diagnostics.audioSampleRate?.toString() ?: "Unavailable"), "Audio channels" to (diagnostics.audioChannels?.toString() ?: "Unavailable"), "Audio bytes sent" to (diagnostics.audioBytesSent?.toString() ?: "Unavailable"), "Audio packets sent" to (diagnostics.audioPacketsSent?.toString() ?: "Unavailable"), "Browser UA" to (diagnostics.browserUserAgent ?: "Unavailable")).forEach { (k, v) -> ListItem(headlineContent = { Text(k, color = Color.White) }, trailingContent = { Text(v, color = Color(0xFF8FA3A9)) }) }; Spacer(Modifier.height(18.dp)); Text("Browser Receiver Debug", style = MaterialTheme.typography.titleLarge, color = Color.White); Text("Last successful stage: ${debug.lastSuccessfulStage ?: "Unavailable"}", color = Color(0xFF35D7C4)); Text("Last error stage: ${debug.lastErrorStage ?: "None"}", color = Color(0xFFFFB4AB)); Text("Exception: ${debug.exceptionClass ?: "None"}", color = Color(0xFF8FA3A9)); Text("Message: ${debug.message ?: "None"}", color = Color(0xFF8FA3A9)); if (debug.previousCrash) Text("Previous CarCast session crashed.", color = Color(0xFFFF6B6B)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { val clipboard = getSystemService(android.content.ClipboardManager::class.java); clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CarCast Debug Report", DebugDiagnostics.report())) }) { Text("COPY DEBUG REPORT") }; OutlinedButton(onClick = { DebugDiagnostics.clear() }) { Text("CLEAR") } } }
                }
            } }
        }
        if (showPin != null) AlertDialog(onDismissRequest = { showPin = null }, title = { Text("Confirm pairing code") }, text = { Column { Text("Enter the temporary six-digit code shown on the receiver."); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) }, label = { Text("Pairing code") }) } }, confirmButton = { TextButton(enabled = pin.length == 6, onClick = { selected = showPin; selectedPin = pin; showPin = null; projection.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()) }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { showPin = null }) { Text("Cancel") } })
        if (manual) ManualConnectDialog(onDismiss = { manual = false }, onConnect = { host, port, code -> selected = DiscoveredReceiver("Manual receiver", java.net.InetAddress.getByName(host), port); selectedPin = code; manual = false; projection.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()) })
    }
    @Composable private fun ManualConnectDialog(onDismiss: () -> Unit, onConnect: (String, Int, String) -> Unit) { var host by remember { mutableStateOf("") }; var port by remember { mutableStateOf("49152") }; var code by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Manual receiver") }, text = { Column { OutlinedTextField(host, { host = it }, label = { Text("IP address") }); OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }); OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Pairing code") }) } }, confirmButton = { TextButton(enabled = host.isNotBlank() && code.length == 6, onClick = { onConnect(host, port.toIntOrNull() ?: 49152, code) }) { Text("Continue") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) }
}

private fun qrBitmap(value: String): Bitmap { val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 256, 256); val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888); for (x in 0 until 256) for (y in 0 until 256) bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE); return bitmap }

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.carcast.mirror.discovery.*
import com.carcast.mirror.service.*
import com.carcast.mirror.core.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.view.SurfaceView
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer

private object CarCastPalette {
    val background = Color(0xFF071014)
    val surface = Color(0xFF101C22)
    val surfaceRaised = Color(0xFF17272F)
    val accent = Color(0xFF35D7C4)
    val accentInk = Color(0xFF05251F)
    val text = Color(0xFFF3F7F7)
    val muted = Color(0xFFAABCC1)
    val outline = Color(0xFF2B424A)
    val accentSoft = Color(0xFF173C3B)
    val warning = Color(0xFFF0C16F)
    val warningSurface = Color(0xFF342B1C)
    val error = Color(0xFFFF968B)
    val errorSurface = Color(0xFF3A2223)
}

private fun carCastColorScheme() = darkColorScheme(
    primary = CarCastPalette.accent,
    onPrimary = CarCastPalette.accentInk,
    primaryContainer = CarCastPalette.accentSoft,
    onPrimaryContainer = Color(0xFFA4F5E9),
    secondary = Color(0xFF67C0C8),
    onSecondary = Color(0xFF062328),
    tertiary = CarCastPalette.warning,
    onTertiary = Color(0xFF30240C),
    background = CarCastPalette.background,
    onBackground = CarCastPalette.text,
    surface = CarCastPalette.surface,
    onSurface = CarCastPalette.text,
    surfaceVariant = CarCastPalette.surfaceRaised,
    onSurfaceVariant = CarCastPalette.muted,
    outline = CarCastPalette.outline,
    outlineVariant = Color(0xFF223840),
    error = CarCastPalette.error,
    onError = Color(0xFF35100D),
    errorContainer = CarCastPalette.errorSurface,
    onErrorContainer = Color(0xFFFFDAD5)
)

class MainActivity : ComponentActivity() {
    private val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    private fun localNetworkGranted() = Build.VERSION.SDK_INT < 37 || checkSelfPermission(localNetworkPermission) == PackageManager.PERMISSION_GRANTED
    private fun requestLocalNetwork() { if (Build.VERSION.SDK_INT >= 37) requestPermissions(arrayOf(localNetworkPermission), 42) }
    private var selected: DiscoveredReceiver? = null
    private var selectedPin = ""
    private var browserApproval = false
    private var audioPermissionRequested = false
    private var nativeAudioPermissionRequested = false
    private val projection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null && selected != null) {
            val target = selected!!
            ContextCompat.startForegroundService(this, Intent(this, NativeWebRtcSenderService::class.java)
                .putExtra(NativeWebRtcSenderService.EXTRA_PROJECTION_RESULT, result.resultCode)
                .putExtra(NativeWebRtcSenderService.EXTRA_PROJECTION_DATA, result.data)
                .putExtra(NativeWebRtcSenderService.EXTRA_HOST, target.host.hostAddress)
                .putExtra(NativeWebRtcSenderService.EXTRA_PORT, target.port)
                .putExtra(NativeWebRtcSenderService.EXTRA_SAS, selectedPin)
                .putExtra(NativeWebRtcSenderService.EXTRA_SENDER_NAME, "CarCast phone"))
        }
    }
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
        installSplashScreen()
        super.onCreate(state)
        DebugDiagnostics.init(this)
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugDiagnostics.uncaught(throwable)
            prior?.uncaughtException(thread, throwable)
        }
        setContent { CarCastApp() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42) recreate()
        if (requestCode == 43) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) AppState.audio { it.copy(status = "Audio: Initializing") }
            else AppState.audio { it.copy(status = "Audio: Unavailable — permission denied; video only") }
            launchBrowserProjection()
        }
        if (requestCode == 44) launchNativeProjection()
    }

    private fun launchBrowserProjection() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && AppState.audio.value.enabled && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && !audioPermissionRequested) {
                audioPermissionRequested = true
                AppState.audio { it.copy(status = "Audio: Waiting for permission") }
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 43)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && AppState.audio.value.enabled && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) AppState.audio { it.copy(status = "Audio: Unavailable — permission denied; video only") }
            DebugDiagnostics.stage(STAGE_BROWSER_APPROVE_CLICKED)
            DebugDiagnostics.stage(STAGE_PROJECTION_CONSENT_LAUNCHED)
            browserProjection.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
        } catch (t: Throwable) {
            DebugDiagnostics.error(STAGE_PROJECTION_CONSENT_LAUNCHED, t)
            AppState.failBrowserReceiver("UNABLE TO START MEDIAPROJECTION: ${t.message ?: t::class.java.simpleName}")
        }
    }

    private fun launchNativeProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && AppState.audio.value.enabled && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && !nativeAudioPermissionRequested) {
            nativeAudioPermissionRequested = true
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 44)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && AppState.audio.value.enabled && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) AppState.audio { it.copy(status = "Audio: Unavailable — permission denied; video only") }
        projection.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
    }

    @Composable
    private fun CarCastApp() {
        var mode by remember { mutableStateOf("cast") }
        var localGranted by remember { mutableStateOf(localNetworkGranted()) }
        var scanning by remember { mutableStateOf(false) }
        var receivers by remember { mutableStateOf(listOf<DiscoveredReceiver>()) }
        var showPin by remember { mutableStateOf<DiscoveredReceiver?>(null) }
        var pin by remember { mutableStateOf("") }
        var manual by remember { mutableStateOf(false) }
        var qualityOpen by remember { mutableStateOf(false) }
        val browserState by AppState.browser.collectAsStateWithLifecycle()
        val receiverState by AppState.receiver.collectAsStateWithLifecycle()
        val nativeReceiver by AppState.nativeReceiver.collectAsStateWithLifecycle()
        val diagnostics by AppState.diagnostics.collectAsStateWithLifecycle()
        val debug by AppState.debug.collectAsStateWithLifecycle()
        val audio by AppState.audio.collectAsStateWithLifecycle()

        val discovery = remember { DiscoveryCoordinator(this) }
        DisposableEffect(mode, localGranted) {
            if (mode == "cast" && localGranted) {
                scanning = true
                discovery.start { receivers = it }
            }
            onDispose { discovery.stop() }
        }

        MaterialTheme(colorScheme = carCastColorScheme()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    BrandMark()
                    Spacer(Modifier.height(18.dp))
                    ModeNavigation(mode = mode, onModeSelected = { mode = it })
                    Spacer(Modifier.height(22.dp))

                    if (mode == "cast") {
                        CastScreen(
                            localGranted = localGranted,
                            scanning = scanning,
                            receivers = receivers,
                            modifier = Modifier.weight(1f),
                            onRequestAccess = { requestLocalNetwork() },
                            onScan = {
                                if (localGranted) {
                                    scanning = true
                                    discovery.start { receivers = it }
                                } else requestLocalNetwork()
                            },
                            onSelectReceiver = { showPin = it },
                            onManualConnect = { manual = true },
                            onSystemCast = { startActivity(Intent(Settings.ACTION_CAST_SETTINGS)) }
                        )
                    } else {
                        val scrollable = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        when (mode) {
                            "receive" -> ReceiveScreen(
                                receiverState = receiverState,
                                nativeReceiver = nativeReceiver,
                                localGranted = localGranted,
                                modifier = scrollable,
                                onStart = {
                                    if (localGranted) startService(Intent(this@MainActivity, NativeReceiverWebRtcService::class.java))
                                    else requestLocalNetwork()
                                },
                                onApprove = { startService(Intent(this@MainActivity, NativeReceiverWebRtcService::class.java).setAction(NativeReceiverWebRtcService.ACTION_APPROVE)) },
                                onDecline = { startService(Intent(this@MainActivity, NativeReceiverWebRtcService::class.java).setAction(NativeReceiverWebRtcService.ACTION_DECLINE)) },
                                onStop = { startService(Intent(this@MainActivity, NativeReceiverWebRtcService::class.java).setAction(NativeReceiverWebRtcService.ACTION_STOP)) }
                            )
                            "browser" -> BrowserScreen(
                                browserState = browserState,
                                localGranted = localGranted,
                                modifier = scrollable,
                                diagnostics = diagnostics,
                                audio = audio,
                                qualityOpen = qualityOpen,
                                onQualityOpenChange = { qualityOpen = it },
                                onLaunchProjection = { launchBrowserProjection() }
                            )
                            "car" -> CarModeScreen(modifier = scrollable)
                            else -> DiagnosticsScreen(
                                diagnostics = diagnostics,
                                debug = debug,
                                modifier = scrollable,
                                onCopyReport = {
                                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CarCast Debug Report", DebugDiagnostics.report()))
                                },
                                onClearReport = { DebugDiagnostics.clear() }
                            )
                        }
                    }
                }
            }
        }

        if (showPin != null) {
            AlertDialog(
                onDismissRequest = { showPin = null },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                title = { Text("Confirm pairing code", fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Enter the temporary six-digit code shown on the receiver.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                            label = { Text("Pairing code") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = pin.length == 6,
                        onClick = {
                            selected = showPin
                            selectedPin = pin
                            showPin = null
                            launchNativeProjection()
                        }
                    ) { Text("Continue") }
                },
                dismissButton = { TextButton(onClick = { showPin = null }) { Text("Cancel") } }
            )
        }

        if (manual) {
            ManualConnectDialog(
                onDismiss = { manual = false },
                onConnect = { host, port, code ->
                    selected = DiscoveredReceiver("Manual receiver", java.net.InetAddress.getByName(host), port)
                    selectedPin = code
                    manual = false
                    launchNativeProjection()
                }
            )
        }
    }

    @Composable
    private fun ModeNavigation(mode: String, onModeSelected: (String) -> Unit) {
        val destinations = listOf(
            "cast" to "Cast screen",
            "receive" to "Receive",
            "browser" to "Browser",
            "car" to "Car mode",
            "diag" to "Diagnostics"
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { (id, label) ->
                val selected = mode == id
                FilterChip(
                    selected = selected,
                    onClick = { onModeSelected(id) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
            }
        }
    }

    @Composable
    private fun CastScreen(
        localGranted: Boolean,
        scanning: Boolean,
        receivers: List<DiscoveredReceiver>,
        modifier: Modifier,
        onRequestAccess: () -> Unit,
        onScan: () -> Unit,
        onSelectReceiver: (DiscoveredReceiver) -> Unit,
        onManualConnect: () -> Unit,
        onSystemCast: () -> Unit
    ) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading(
                title = "Find your display",
                subtitle = "Securely mirror to a nearby screen on your local network."
            )

            if (!localGranted) {
                SurfaceCard {
                    StatusBadge("PERMISSION NEEDED", CarCastPalette.warning, CarCastPalette.warningSurface)
                    Text("Local network access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "CarCast needs local network access to discover and securely connect to a receiver. Screen content is not uploaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRequestAccess,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Allow local network access") }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Nearby displays", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    StatusBadge(
                        if (receivers.isEmpty()) "SCANNING" else "${receivers.size} FOUND",
                        if (receivers.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        CarCastPalette.accentSoft
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 2.dp)
                ) {
                    if (receivers.isEmpty()) {
                        item {
                            SurfaceCard {
                                Text("No displays found yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Keep this screen open while CarCast checks your local network. You can also connect by IP or use Android System Cast.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(receivers.size) { i ->
                            val target = receivers[i]
                            ReceiverCard(target = target, onClick = { onSelectReceiver(target) })
                        }
                    }
                }

                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(if (scanning) "Scan again" else "Scan for displays", fontWeight = FontWeight.SemiBold) }

                OutlinedButton(
                    onClick = onSystemCast,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Use Android System Cast", textAlign = TextAlign.Center) }
            }
        }
    }

    @Composable
    private fun ReceiverCard(target: DiscoveredReceiver, onClick: () -> Unit) {
        val nativeReceiver = target.protocol == "CarCast Native"
        OutlinedButton(
            enabled = nativeReceiver,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("TV", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(target.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (nativeReceiver) "CarCast Receiver · Available on local network" else "${target.protocol} · Media only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (target.protocol == "DLNA") "MEDIA ONLY — not full-screen mirroring" else "Secure approval required on receiver",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (target.protocol == "DLNA") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    @Composable
    private fun ReceiveScreen(receiverState: ReceiverUiState, nativeReceiver: NativeReceiverMetrics, localGranted: Boolean, modifier: Modifier, onStart: () -> Unit, onApprove: () -> Unit, onDecline: () -> Unit, onStop: () -> Unit) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading(receiverState.friendlyName, "Ready to receive from a nearby CarCast phone.")
            SurfaceCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CarCast Receiver", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    StatusBadge(receiverState.status.uppercase())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Keep this screen open and select this receiver from the sender phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Same Wi-Fi required", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                AndroidView(
                    factory = { context -> SurfaceViewRenderer(context).also { NativeReceiverRenderer.attach(it) } },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (receiverState.pendingSender != null) {
                SurfaceCard {
                    StatusBadge("APPROVAL REQUIRED", MaterialTheme.colorScheme.tertiary, CarCastPalette.warningSurface)
                    Text("${receiverState.pendingSender} wants to cast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Confirm that both screens show the same verification code:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(receiverState.pendingSas?.chunked(3)?.joinToString(" ") ?: "------", style = MaterialTheme.typography.headlineMedium, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onApprove, modifier = Modifier.weight(1f).heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) { Text("CONNECT") }
                        OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f).heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) { Text("DECLINE") }
                    }
                }
            } else if (!receiverState.active || receiverState.status == "Stopped" || receiverState.status == "Failed") {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) { Text("Start receiver", fontWeight = FontWeight.SemiBold) }
            } else if (receiverState.status == "Connected") {
                Text("Connected · ${nativeReceiver.audioTrackStatus}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) { Text("Stop receiver") }
            }
        }
    }

    @Composable
    private fun BrowserScreen(
        browserState: BrowserUiState,
        localGranted: Boolean,
        modifier: Modifier,
        diagnostics: LiveDiagnostics,
        audio: AudioUiState,
        qualityOpen: Boolean,
        onQualityOpenChange: (Boolean) -> Unit,
        onLaunchProjection: () -> Unit
    ) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading("Browser receiver", "Stream to a TV or computer browser over your local network.")

            when {
                browserState.status == BrowserStatus.STARTING -> {
                    SurfaceCard {
                        StatusBadge("STARTING", MaterialTheme.colorScheme.primary, CarCastPalette.accentSoft)
                        Text("Starting Browser Receiver…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Starting…") }
                    }
                }
                browserState.status == BrowserStatus.FAILED -> {
                    SurfaceCard {
                        StatusBadge("NEEDS ATTENTION", MaterialTheme.colorScheme.error, CarCastPalette.errorSurface)
                        Text(if (browserState.error?.contains("disconnected", true) == true || browserState.error?.contains("Wi-Fi", true) == true) "Connection lost" else "Couldn’t connect", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        Text(browserState.error ?: "Try reconnecting to the TV browser.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { startService(Intent(this@MainActivity, BrowserReceiverService::class.java)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Reconnect") }
                            OutlinedButton(onClick = { startService(Intent(this@MainActivity, BrowserReceiverService::class.java).setAction(BrowserReceiverService.ACTION_STOP)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Stop Casting") }
                        }
                    }
                }
                browserState.status in setOf(BrowserStatus.WAITING_FOR_BROWSER, BrowserStatus.APPROVAL_REQUIRED, BrowserStatus.NEGOTIATING, BrowserStatus.CONNECTED) -> {
                    ActiveBrowserReceiver(browserState, diagnostics, audio, qualityOpen, onQualityOpenChange, onLaunchProjection)
                }
                else -> {
                    SurfaceCard {
                        Text("Ready when you are", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Start a temporary local receiver page with WebRTC signaling.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                if (localGranted) startService(Intent(this@MainActivity, BrowserReceiverService::class.java))
                                else requestLocalNetwork()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Start Browser Receiver", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActiveBrowserReceiver(
        browserState: BrowserUiState,
        diagnostics: LiveDiagnostics,
        audio: AudioUiState,
        qualityOpen: Boolean,
        onQualityOpenChange: (Boolean) -> Unit,
        onLaunchProjection: () -> Unit
    ) {
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Browser session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusBadge(browserState.status.name.replace('_', ' '))
            }
            Box {
                OutlinedButton(
                    onClick = { onQualityOpenChange(true) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Quality: ${when (browserState.qualityMode) { BrowserQualityMode.AUTO -> "Auto"; BrowserQualityMode.LOW_LATENCY -> "Smooth"; BrowserQualityMode.FULL_HD -> "High Quality"; BrowserQualityMode.HD -> "Smooth" }}") }
                DropdownMenu(expanded = qualityOpen, onDismissRequest = { onQualityOpenChange(false) }) {
                    listOf(BrowserQualityMode.AUTO to "Auto", BrowserQualityMode.LOW_LATENCY to "Smooth", BrowserQualityMode.FULL_HD to "High Quality").forEach { (qualityMode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onQualityOpenChange(false)
                                startService(Intent(this@MainActivity, BrowserReceiverService::class.java)
                                    .setAction(BrowserReceiverService.ACTION_SET_QUALITY)
                                    .putExtra(BrowserReceiverService.EXTRA_QUALITY, qualityMode.name))
                                AppState.browser { it.copy(qualityMode = qualityMode) }
                            }
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Open on your TV", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        "http://${browserState.address}:${browserState.httpPort}",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            val qr = remember(browserState.address, browserState.httpPort) { qrBitmap("http://${browserState.address}:${browserState.httpPort}") }
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(18.dp),
                color = Color.White
            ) {
                Image(
                    qr.asImageBitmap(),
                    contentDescription = "Browser receiver QR",
                    modifier = Modifier.padding(10.dp).size(184.dp),
                    contentScale = ContentScale.Fit
                )
            }
            DiagnosticRow("Browser", browserState.browserUserAgent ?: "Waiting for browser")
            DiagnosticRow("LAN address", browserState.remoteAddress ?: "Waiting for browser")
        }

        SurfaceCard {
            Text("Actual stream", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamMetric("Resolution", diagnostics.resolution, Modifier.weight(1f))
                StreamMetric("Frame rate", diagnostics.fps?.let { "%.1f FPS".format(it) } ?: "FPS unavailable", Modifier.weight(1f))
                StreamMetric("Bitrate", diagnostics.bitrateKbps?.let { "$it kbps" } ?: "bitrate unavailable", Modifier.weight(1f))
            }
            diagnostics.qualityNote?.let {
                StatusNotice(it, CarCastPalette.warning, CarCastPalette.warningSurface)
            }
        }

        SurfaceCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Share device audio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Send supported phone audio to the receiver.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = audio.enabled,
                    onCheckedChange = { value ->
                        AppState.audio { it.copy(enabled = value, status = if (value) "Audio: Waiting for permission" else "Audio: Disabled") }
                        if (browserState.status != BrowserStatus.STOPPED) startService(Intent(this@MainActivity, BrowserReceiverService::class.java)
                            .setAction(BrowserReceiverService.ACTION_SET_AUDIO)
                            .putExtra(BrowserReceiverService.EXTRA_AUDIO, value))
                    },
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                )
            }
            Text("Audio: ${audio.status}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        }

        if (browserState.status == BrowserStatus.APPROVAL_REQUIRED) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onLaunchProjection, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Approve") }
                OutlinedButton(
                    onClick = { startService(Intent(this@MainActivity, BrowserReceiverService::class.java).setAction(BrowserReceiverService.ACTION_REJECT)) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Reject") }
            }
        }

        OutlinedButton(
            onClick = { startService(Intent(this@MainActivity, BrowserReceiverService::class.java).setAction(BrowserReceiverService.ACTION_STOP)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("Stop Receiver") }
    }

    @Composable
    private fun CarModeScreen(modifier: Modifier) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading("Car mode", "Understand which in-vehicle paths CarCast supports.")
            SurfaceCard {
                StatusBadge("COMPATIBILITY NOTE", CarCastPalette.warning, CarCastPalette.warningSurface)
                Text(
                    "Factory Android Auto displays are not generic screen receivers. CarCast cannot bypass Android Auto or inject arbitrary phone pixels into a Nissan Rogue factory display.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SurfaceCard {
                Text("Supported paths", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SupportedPath("01", "Android-powered head unit with CarCast Receiver installed directly.")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SupportedPath("02", "Android Automotive OS only through an eligible, reviewed parked-app category.")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SupportedPath("03", "External Miracast or receiver hardware using Android's public system handoff.")
            }
        }
    }

    @Composable
    private fun DiagnosticsScreen(
        diagnostics: LiveDiagnostics,
        debug: BrowserDebugReport,
        modifier: Modifier,
        onCopyReport: () -> Unit,
        onClearReport: () -> Unit
    ) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading("Diagnostics", "Session health and browser receiver troubleshooting.")
            MetricSection(
                "Connection",
                listOf(
                    "Protocol" to diagnostics.protocol,
                    "State" to diagnostics.state,
                    "Receiver" to diagnostics.receiver,
                    "Codec" to diagnostics.codec
                )
            )
            MetricSection(
                "Video",
                listOf(
                    "Capture resolution" to diagnostics.captureResolution,
                    "Sent resolution" to diagnostics.resolution,
                    "FPS" to (diagnostics.fps?.toString() ?: "Unavailable"),
                    "Encoded FPS" to (diagnostics.encodedFps?.toString() ?: "Unavailable"),
                    "Quality limitation" to (diagnostics.qualityLimitationReason ?: "None reported")
                )
            )
            MetricSection(
                "Network",
                listOf(
                    "Bitrate" to (diagnostics.bitrateKbps?.let { "$it kbps" } ?: "Unavailable"),
                    "Bytes/sec" to (diagnostics.bytesPerSecond?.toString() ?: "Unavailable"),
                    "Frames sent" to (diagnostics.framesSent?.toString() ?: "Unavailable"),
                    "ICE state" to (diagnostics.iceState ?: "Unavailable"),
                    "Selected candidate pair" to (diagnostics.selectedCandidatePair ?: "Unavailable"),
                    "Local candidate type" to (diagnostics.localCandidateType ?: "Unavailable"),
                    "Remote candidate type" to (diagnostics.remoteCandidateType ?: "Unavailable"),
                    "RTT" to (diagnostics.rttMs?.let { "$it ms" } ?: "Unavailable"),
                    "Packets lost" to (diagnostics.packetsLost?.toString() ?: "Unavailable"),
                    "Available outgoing bitrate" to (diagnostics.availableOutgoingBitrateKbps?.let { "$it kbps" } ?: "Unavailable")
                )
            )
            MetricSection(
                "Audio",
                listOf(
                    "Audio track" to (diagnostics.audioTrackState ?: "Missing"),
                    "Audio source" to (diagnostics.audioCaptureSource ?: "Unavailable"),
                    "Audio codec" to (diagnostics.audioCodec ?: "Unavailable"),
                    "Audio sample rate" to (diagnostics.audioSampleRate?.toString() ?: "Unavailable"),
                    "Audio channels" to (diagnostics.audioChannels?.toString() ?: "Unavailable"),
                    "Audio bytes sent" to (diagnostics.audioBytesSent?.toString() ?: "Unavailable"),
                    "Audio packets sent" to (diagnostics.audioPacketsSent?.toString() ?: "Unavailable")
                )
            )
            MetricSection("Browser", listOf("Browser UA" to (diagnostics.browserUserAgent ?: "Unavailable")))

            SurfaceCard {
                Text("Browser receiver debug", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DiagnosticRow("Last successful stage", debug.lastSuccessfulStage ?: "Unavailable")
                DiagnosticRow("Last error stage", debug.lastErrorStage ?: "None")
                DiagnosticRow("Exception", debug.exceptionClass ?: "None")
                DiagnosticRow("Message", debug.message ?: "None")
                if (debug.previousCrash) StatusNotice("Previous CarCast session crashed.", MaterialTheme.colorScheme.error, CarCastPalette.errorSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCopyReport, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) { Text("COPY DEBUG REPORT") }
                    OutlinedButton(onClick = onClearReport, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) { Text("CLEAR") }
                }
            }
        }
    }

    @Composable
    private fun ManualConnectDialog(onDismiss: () -> Unit, onConnect: (String, Int, String) -> Unit) {
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("49152") }
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Manual receiver", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(host, { host = it }, label = { Text("IP address") }, singleLine = true)
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true)
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Pairing code") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = host.isNotBlank() && code.length == 6, onClick = { onConnect(host, port.toIntOrNull() ?: 49152, code) }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    @Composable
    private fun BrandMark() {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF15383C), Color(0xFF10252E))))
            ) {
                Image(
                    painterResource(com.carcast.mirror.R.drawable.carcast_icon_foreground),
                    contentDescription = stringResource(com.carcast.mirror.R.string.app_name),
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(com.carcast.mirror.R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(com.carcast.mirror.R.string.brand_tagline),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            StatusBadge("LOCAL", MaterialTheme.colorScheme.primary, CarCastPalette.accentSoft)
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun StatusBadge(label: String, foreground: Color = MaterialTheme.colorScheme.primary, background: Color = CarCastPalette.accentSoft) {
    Surface(shape = CircleShape, color = background) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.45.sp),
            color = foreground,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusNotice(message: String, foreground: Color, background: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = background) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = foreground)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MetricSection(title: String, values: List<Pair<String, String>>) {
    SurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        values.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DiagnosticRow(label, value)
        }
    }
}

@Composable
private fun StreamMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SupportedPath(number: String, description: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusBadge(number)
        Text(description, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun qrBitmap(value: String): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 256, 256)
    val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    for (x in 0 until 256) for (y in 0 until 256) bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    return bitmap
}

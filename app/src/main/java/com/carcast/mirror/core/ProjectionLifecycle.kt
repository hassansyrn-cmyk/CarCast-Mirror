package com.carcast.mirror.core

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Debug-only lifecycle accounting for one single-use Browser projection session. */
object ProjectionLifecycle {
    private val session = AtomicReference("")
    private val consent = AtomicInteger()
    private val getProjection = AtomicInteger()
    private val webRtcSession = AtomicInteger()
    private val startCapture = AtomicInteger()
    private val createVirtualDisplay = AtomicInteger()
    private val approveIntent = AtomicInteger()
    private val approveCommand = AtomicInteger()

    fun begin(id: String) { session.set(id); consent.set(0); getProjection.set(0); webRtcSession.set(0); startCapture.set(0); createVirtualDisplay.set(0); approveIntent.set(0); approveCommand.set(0) }
    fun consentResult() = consent.incrementAndGet()
    fun projectionRetrieved(): Boolean = getProjection.incrementAndGet().also { if (it > 1) duplicate("getMediaProjection") } == 1
    fun webRtcCreated() = webRtcSession.incrementAndGet()
    fun captureStarted() = startCapture.incrementAndGet()
    fun virtualDisplayCreated(): Boolean = createVirtualDisplay.incrementAndGet().also { if (it > 1) duplicate("createVirtualDisplay") } == 1
    fun approveIntent() = approveIntent.incrementAndGet()
    fun approveCommand() = approveCommand.incrementAndGet()
    fun snapshot(): String = "sessionId=${session.get()} consent=${consent.get()} getMediaProjection=${getProjection.get()} webRtcSession=${webRtcSession.get()} startCapture=${startCapture.get()} createVirtualDisplay=${createVirtualDisplay.get()} approveIntent=${approveIntent.get()} approveCommand=${approveCommand.get()}"
    fun duplicate(stage: String) { DebugDiagnostics.error("DUPLICATE_MEDIA_PROJECTION_USE_PREVENTED", IllegalStateException("Session ${session.get()} attempted $stage; ${snapshot()}")) }
}

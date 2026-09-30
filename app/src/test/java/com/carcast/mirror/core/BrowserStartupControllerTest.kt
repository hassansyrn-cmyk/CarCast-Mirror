package com.carcast.mirror.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserStartupControllerTest {
    @Test fun bothServersMustBindBeforeReady() {
        val c = BrowserStartupController()
        c.start(); c.httpBound(43121)
        assertEquals(BrowserStartupController.Phase.HTTP_BOUND, c.phase)
        c.signalingBound(43122); c.ready()
        assertEquals(BrowserStartupController.Phase.WAITING_FOR_BROWSER, c.phase)
        assertNull(c.error)
    }

    @Test fun signalingFailureRemainsVisibleUntilNextStart() {
        val c = BrowserStartupController()
        c.start(); c.failed("WebSocket signaling server failed to bind")
        assertEquals(BrowserStartupController.Phase.FAILED, c.phase)
        assertTrue(c.error!!.contains("failed to bind"))
        c.start()
        assertEquals(BrowserStartupController.Phase.STARTING, c.phase)
        assertNull(c.error)
    }

    @Test fun resetClearsAllBrowserSessionStateAndDiagnostics() {
        AppState.browser { it.copy(status = BrowserStatus.CONNECTED, address = "192.168.1.10", httpPort = 4000, signalPort = 4001, browserUserAgent = "test", remoteAddress = "192.168.1.20", sessionStartedAtMs = 1234, error = "old") }
        AppState.diagnostics { it.copy(state = "CONNECTED", browserUserAgent = "test", iceState = "CONNECTED", framesSent = 4) }
        AppState.resetBrowserReceiverState()
        assertEquals(BrowserUiState(), AppState.browser.value)
        assertEquals(LiveDiagnostics(), AppState.diagnostics.value)
    }

    @Test fun failureKeepsDiagnosticsButInvalidatesObsoleteBrowserEndpoint() {
        AppState.browser { it.copy(status = BrowserStatus.CONNECTED, address = "192.168.1.10", httpPort = 4000, signalPort = 4001, remoteAddress = "192.168.1.20") }
        AppState.diagnostics { it.copy(state = "DISCONNECTED", iceState = "FAILED", qualityLimitationReason = "bandwidth") }
        AppState.failBrowserReceiver("Wi-Fi connection changed — tap Reconnect")
        assertEquals(BrowserStatus.FAILED, AppState.browser.value.status)
        assertEquals("", AppState.browser.value.address)
        assertEquals(0, AppState.browser.value.httpPort)
        assertEquals("DISCONNECTED", AppState.diagnostics.value.state)
        assertEquals("FAILED", AppState.diagnostics.value.iceState)
        assertEquals("bandwidth", AppState.diagnostics.value.qualityLimitationReason)
    }
}

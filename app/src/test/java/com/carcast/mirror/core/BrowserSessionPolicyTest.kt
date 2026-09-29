package com.carcast.mirror.core

import org.junit.Assert.*
import org.junit.Test

class BrowserSessionPolicyTest {
    @Test fun authorizedClientMustBeApproved() { var t = 1000L; val p = BrowserSessionPolicy { t }; assertTrue(p.authorize("tv-a")); assertFalse(p.valid("tv-a")); assertTrue(p.approve(true)); assertTrue(p.valid("tv-a")); assertFalse(p.valid("tv-b")) }
    @Test fun tokenExpiresAndRejectsClient() { var t = 1000L; val p = BrowserSessionPolicy { t }; t += 120_001; assertFalse(p.authorize("tv-a")); assertEquals(BrowserApproval.EXPIRED, p.approval) }
    @Test fun secondBrowserIsRejected() { val p = BrowserSessionPolicy { 1000L }; assertTrue(p.authorize("tv-a")); assertFalse(p.authorize("tv-b")); assertTrue(p.approve(true)); assertFalse(p.valid("tv-b")) }
}

class RotationConfigTest {
    @Test fun rotatedProfileSwapsDimensionsAndOrientation() { val a = StreamConfig(codec = "video/avc", width = 1280, height = 720, orientation = 1, fps = 30, bitrate = 4_000_000, audio = false, lowLatency = true); val b = a.copy(width = a.height, height = a.width, orientation = 2); assertEquals(720, b.width); assertEquals(1280, b.height); assertNotEquals(a.orientation, b.orientation) }
}

class SasSessionLifetimeTest {
    @Test fun sasDoesNotPretendToExpireBeforeTlsIdentityChanges() {
        assertEquals(Long.MAX_VALUE, com.carcast.mirror.service.ReceiverSessionState.run { begin("123456"); expiresAtMs })
        com.carcast.mirror.service.ReceiverSessionState.clear()
    }
}

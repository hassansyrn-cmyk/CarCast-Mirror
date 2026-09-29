package com.carcast.mirror.core

import org.junit.Assert.*
import org.junit.Test

class ReconnectPolicyTest {
    @Test fun boundedExponentialBackoff() { val p = ReconnectPolicy(5, 15_000L); assertEquals(1000L, p.nextDelayMs()); assertEquals(2000L, p.nextDelayMs()); assertEquals(4000L, p.nextDelayMs()); assertEquals(8000L, p.nextDelayMs()); assertEquals(15000L, p.nextDelayMs()); assertNull(p.nextDelayMs()) }
}

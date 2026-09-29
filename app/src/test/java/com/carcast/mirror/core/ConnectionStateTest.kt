package com.carcast.mirror.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStateTest {
    @Test fun stateEnumContainsReconnectLifecycle() { assertEquals(ConnectionState.RECONNECTING, ConnectionState.valueOf("RECONNECTING")); assertEquals(ConnectionState.AUTHENTICATING, ConnectionState.valueOf("AUTHENTICATING")) }
}

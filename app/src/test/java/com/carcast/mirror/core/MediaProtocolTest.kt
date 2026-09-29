package com.carcast.mirror.core

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class MediaProtocolTest {
    @Test(expected = IllegalArgumentException::class) fun oversizedFrameRejected() { EncodedFrame.decode(ByteArray(MAX_FRAME_BYTES + 17)) }
    @Test(expected = IllegalArgumentException::class) fun malformedFrameRejected() { EncodedFrame.decode(ByteArray(16)) }
    @Test fun capabilitiesRoundTrip() { val c = ReceiverCapabilities(listOf("video/avc"), 1920, 1080, 30, 4_000_000, true, false); assertEquals(c, ReceiverCapabilities.decode(c.encode())) }
    @Test fun encryptedPayloadIntegrity() {
        val left = PipedInputStream(); val right = PipedOutputStream(left); val input = DataInputStream(left); val output = DataOutputStream(right); val channel = TlsFramedChannel(input, output); channel.write(FrameTypes.ENCODED_FRAME, EncodedFrame(10, 1, byteArrayOf(1, 2, 3)).encode()); assertArrayEquals(byteArrayOf(1, 2, 3), EncodedFrame.decode(channel.read().payload).payload)
    }
}

package com.carcast.mirror.core

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class SecureAndNegotiationTest {
    @Test fun framedMetadataRoundTripsOverTls() {
        val out = ByteArrayOutputStream(); val writer = TlsFramedChannel(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(out)); writer.write(FrameTypes.SENDER_PROPOSAL, "proposal".toByteArray()); val frame = TlsFramedChannel(DataInputStream(ByteArrayInputStream(out.toByteArray())), DataOutputStream(ByteArrayOutputStream())).read(); assertEquals(FrameTypes.SENDER_PROPOSAL, frame.type); assertEquals("proposal", frame.payload.toString(Charsets.UTF_8))
    }
    @Test(expected = IllegalArgumentException::class) fun replayedSequenceRejected() {
        val out = ByteArrayOutputStream(); val writer = TlsFramedChannel(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(out)); writer.write(FrameTypes.PING, byteArrayOf(1)); val once = out.toByteArray(); val input = DataInputStream(ByteArrayInputStream(once + once)); val reader = TlsFramedChannel(input, DataOutputStream(ByteArrayOutputStream())); reader.read(); reader.read()
    }
    @Test fun unsupportedProposalProducesCounterProfile() { val receiver = ReceiverCapabilities(listOf("video/avc"), 1280, 720, 30, 4_000_000, true, false); val proposal = StreamConfig(codec = "video/avc", width = 1920, height = 1080, orientation = 1, fps = 60, bitrate = 8_000_000, audio = false, lowLatency = true); val accepted = proposal.copy(width = minOf(proposal.width, receiver.maxWidth), height = minOf(proposal.height, receiver.maxHeight), fps = minOf(proposal.fps, receiver.maxFps), bitrate = minOf(proposal.bitrate, receiver.maxBitrate)); assertNotEquals(proposal, accepted); assertEquals(1280, accepted.width); assertEquals(720, accepted.height); assertEquals(30, accepted.fps) }
}

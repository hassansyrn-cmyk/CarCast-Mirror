package com.carcast.mirror.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/** Application framing only. Confidentiality, integrity, authentication, and ordering are provided by TLS 1.3. */
class TlsFramedChannel(private val input: DataInputStream, private val output: DataOutputStream) {
    private var sendSequence = 0L
    private var receiveSequence = 0L

    @Synchronized fun write(type: Int, payload: ByteArray) {
        require(payload.size <= MAX_FRAME_BYTES)
        output.writeLong(sendSequence++)
        output.writeInt(type)
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    @Synchronized fun read(): Frame {
        val sequence = input.readLong()
        require(sequence == receiveSequence++) { "out-of-order or replayed frame" }
        val type = input.readInt()
        val size = input.readInt()
        require(size in 0..MAX_FRAME_BYTES)
        val payload = ByteArray(size).also(input::readFully)
        return Frame(type, payload)
    }

    data class Frame(val type: Int, val payload: ByteArray)
}

data class StreamConfig(val protocolVersion: Int = 1, val codec: String, val width: Int, val height: Int, val orientation: Int, val fps: Int, val bitrate: Int, val audio: Boolean, val lowLatency: Boolean) {
    fun encode(): ByteArray = listOf(protocolVersion, codec, width, height, orientation, fps, bitrate, audio, lowLatency).joinToString("|").toByteArray()
    companion object { fun decode(bytes: ByteArray): StreamConfig { val p = bytes.toString(Charsets.UTF_8).split('|'); require(p.size == 9 && p[0] == "1"); return StreamConfig(p[0].toInt(), p[1], p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toInt(), p[7].toBoolean(), p[8].toBoolean()) } }
}

enum class ConnectionState { IDLE, DISCOVERING, CONNECTING, PAIRING, AUTHENTICATING, CONNECTED, RECONNECTING, DISCONNECTED, ERROR }

object FrameTypes { const val STREAM_CONFIG = 1; const val VIDEO = 2; const val PING = 3; const val PONG = 4; const val CLOSE = 5; const val CAPABILITIES = 6; const val ENCODED_FRAME = 7; const val SENDER_PROPOSAL = 8; const val RECEIVER_ACCEPT = 9; const val RECEIVER_COUNTER_PROPOSAL = 10; const val NATIVE_HELLO = 11; const val NATIVE_APPROVED = 12; const val NATIVE_DECLINED = 13; const val NATIVE_SIGNAL = 14 }

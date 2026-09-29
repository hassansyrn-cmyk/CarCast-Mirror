package com.carcast.mirror.core

import java.nio.ByteBuffer

const val MAX_FRAME_BYTES = 8 * 1024 * 1024

data class ReceiverCapabilities(val codecs: List<String>, val maxWidth: Int, val maxHeight: Int, val maxFps: Int, val maxBitrate: Int, val supportsAvc: Boolean, val supportsHevc: Boolean) {
    fun encode() = listOf(codecs.joinToString(","), maxWidth, maxHeight, maxFps, maxBitrate, supportsAvc, supportsHevc).joinToString("|").toByteArray()
    companion object { fun decode(bytes: ByteArray): ReceiverCapabilities { val p = bytes.toString(Charsets.UTF_8).split('|'); require(p.size == 7); return ReceiverCapabilities(p[0].split(',').filter { it.isNotBlank() }, p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toBoolean(), p[6].toBoolean()) } }
}

data class EncodedFrame(val ptsUs: Long, val flags: Int, val payload: ByteArray) {
    init { require(payload.size in 1..MAX_FRAME_BYTES) }
    fun encode(): ByteArray { val b = ByteBuffer.allocate(8 + 4 + 4 + payload.size); b.putLong(ptsUs); b.putInt(flags); b.putInt(payload.size); b.put(payload); return b.array() }
    companion object { fun decode(bytes: ByteArray): EncodedFrame { require(bytes.size >= 16 && bytes.size <= MAX_FRAME_BYTES + 16); val b = ByteBuffer.wrap(bytes); val pts = b.long; val flags = b.int; val size = b.int; require(size in 1..MAX_FRAME_BYTES && size == b.remaining()); return EncodedFrame(pts, flags, ByteArray(size).also(b::get)) } }
}

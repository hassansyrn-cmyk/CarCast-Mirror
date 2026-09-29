package com.carcast.mirror.core

class ReconnectPolicy(private val maxAttempts: Int = 5, private val maxDelayMs: Long = 15_000) {
    private var attempt = 0
    fun nextDelayMs(): Long? { if (attempt >= maxAttempts) return null; val delay = (1_000L shl attempt.coerceAtMost(4)).coerceAtMost(maxDelayMs); attempt++; return delay }
    fun reset() { attempt = 0 }
    fun attempts() = attempt
}

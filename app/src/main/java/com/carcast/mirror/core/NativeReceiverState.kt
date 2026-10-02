package com.carcast.mirror.core

import android.content.Context
import android.os.Build
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/** Stable, user-facing identity; never derived from hardware identifiers. */
object ReceiverIdentityStore {
    private const val PREFS = "carcast_receiver_identity"
    private const val KEY_NAME = "friendly_name"

    fun name(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_NAME, "CarCast Receiver") ?: "CarCast Receiver"

    fun setName(context: Context, value: String) {
        val safe = value.trim().take(40).ifBlank { "CarCast Receiver" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, safe).apply()
    }
}

data class NativeReceiverMetrics(
    val discoveryState: String = "Stopped",
    val localAddress: String? = null,
    val signalingState: String = "Idle",
    val webRtcState: String = "Idle",
    val iceState: String = "Idle",
    val videoCodec: String? = null,
    val resolution: String? = null,
    val fps: Double? = null,
    val bitrateKbps: Int? = null,
    val audioTrackStatus: String = "Missing",
    val audioCodec: String? = null,
    val rttMs: Long? = null,
    val packetsLost: Long? = null,
    val jitterMs: Double? = null,
    val lastDisconnectReason: String? = null,
    val lastException: String? = null
)

object NativeReceiverRuntime {
    @Volatile var enabled = false
    @Volatile var friendlyName = "CarCast Receiver"
    @Volatile var pendingSender: String? = null
    @Volatile var pendingSas: String? = null
    @Volatile var activeSender: String? = null
    @Volatile var approved = false
    private val decision = AtomicReference<Boolean?>(null)
    private var latch: CountDownLatch? = null

    @Synchronized fun beginApproval(sender: String, sas: String) {
        pendingSender = sender
        pendingSas = sas
        approved = false
        decision.set(null)
        latch = CountDownLatch(1)
    }

    @Synchronized fun decide(allow: Boolean) {
        decision.set(allow)
        approved = allow
        latch?.countDown()
    }

    fun awaitDecision(timeoutMs: Long): Boolean = latch?.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) == true && decision.get() == true

    @Synchronized fun clearApproval() {
        pendingSender = null
        pendingSas = null
        approved = false
        decision.set(null)
        latch = null
    }

    fun reset() {
        enabled = false
        friendlyName = "CarCast Receiver"
        activeSender = null
        clearApproval()
    }
}

fun defaultReceiverName(): String = if (Build.MODEL.isNullOrBlank()) "CarCast Receiver" else "CarCast Receiver"

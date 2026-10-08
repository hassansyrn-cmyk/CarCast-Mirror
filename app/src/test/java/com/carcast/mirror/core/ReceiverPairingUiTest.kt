package com.carcast.mirror.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverPairingUiTest {
    @Test
    fun showsCodeWhenReceiverIsReadyAndWaitingForSender() {
        val state = ReceiverUiState(active = true, pairingCode = "123456", status = "Ready to receive")

        assertTrue(state.showsStandalonePairingCode())
    }

    @Test
    fun hidesStandaloneCodeWhenApprovalCardOwnsTheCode() {
        val state = ReceiverUiState(
            active = true,
            pairingCode = "123456",
            status = "Approval required",
            pendingSender = "CarCast phone",
            pendingSas = "123456"
        )

        assertFalse(state.showsStandalonePairingCode())
    }

    @Test
    fun hidesCodeWhenStoppedConnectedOrNotYetReady() {
        assertFalse(ReceiverUiState(pairingCode = "123456").showsStandalonePairingCode())
        assertFalse(ReceiverUiState(active = true, pairingCode = "123456", status = "Starting").showsStandalonePairingCode())
        assertFalse(ReceiverUiState(active = true, pairingCode = "123456", status = "Connected").showsStandalonePairingCode())
    }

    @Test
    fun doesNotShowMalformedCode() {
        val state = ReceiverUiState(active = true, pairingCode = "12-456", status = "Ready to receive")

        assertFalse(state.showsStandalonePairingCode())
    }
}

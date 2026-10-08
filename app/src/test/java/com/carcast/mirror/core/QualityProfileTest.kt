package com.carcast.mirror.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityProfileTest {
    @Test fun profilesUseRequestedTargets() {
        assertEquals(854, BrowserQualityMode.LOW_LATENCY.profile().maxWidth)
        assertEquals(1_280, BrowserQualityMode.HD.profile().maxWidth)
        assertEquals(1_920, BrowserQualityMode.FULL_HD.profile().maxWidth)
        assertEquals(30, BrowserQualityMode.FULL_HD.profile().fps)
        assertTrue(BrowserQualityMode.FULL_HD.profile().targetBitrateBps > BrowserQualityMode.HD.profile().targetBitrateBps)
        assertTrue(BrowserQualityMode.FULL_HD.profile().minBitrateBps < 1_500_000)
    }

    @Test fun lowLatencyProfileTradesResolutionForLowerBitrate() {
        val smooth = BrowserQualityMode.LOW_LATENCY.profile()
        assertEquals(854, smooth.maxWidth)
        assertEquals(480, smooth.maxHeight)
        assertEquals(30, smooth.fps)
        assertTrue(smooth.targetBitrateBps < BrowserQualityMode.HD.profile().targetBitrateBps)
    }

    @Test fun severeLowFpsIsConstrainedOnFirstSample() {
        val next = QualityAdaptation.next(QualityAdaptationState(), BrowserQualityMode.FULL_HD, 40, 0, 8.0, 30)
        assertEquals(1, next.constrainedSamples)
        assertEquals(0, next.healthySamples)
    }

    @Test fun adaptationCountsNetworkConstraints() {
        val next = QualityAdaptation.next(QualityAdaptationState(), BrowserQualityMode.AUTO, 240, 20, 15.0, 30)
        assertEquals(1, next.constrainedSamples)
        assertEquals(0, next.healthySamples)
    }

    @Test fun healthySamplesAccumulateOnlyWithoutConstraint() {
        val next = QualityAdaptation.next(QualityAdaptationState(), BrowserQualityMode.AUTO, 40, 0, 30.0, 30)
        assertEquals(1, next.healthySamples)
        assertEquals(0, next.constrainedSamples)
    }
}

package com.carcast.mirror.core

enum class BrowserQualityMode { AUTO, LOW_LATENCY, HD, FULL_HD }

data class BrowserQualityProfile(
    val mode: BrowserQualityMode,
    val maxWidth: Int,
    val maxHeight: Int,
    val fps: Int,
    val targetBitrateBps: Int,
    val minBitrateBps: Int
)

fun BrowserQualityMode.profile(): BrowserQualityProfile = when (this) {
    BrowserQualityMode.LOW_LATENCY -> BrowserQualityProfile(this, 854, 480, 30, 1_800_000, 300_000)
    BrowserQualityMode.HD -> BrowserQualityProfile(this, 1280, 720, 30, 4_000_000, 700_000)
    BrowserQualityMode.FULL_HD -> BrowserQualityProfile(this, 1920, 1080, 30, 6_000_000, 1_200_000)
    BrowserQualityMode.AUTO -> BrowserQualityProfile(this, 1280, 720, 30, 3_500_000, 600_000)
}

data class QualityAdaptationState(val healthySamples: Int = 0, val constrainedSamples: Int = 0)

object QualityAdaptation {
    fun next(state: QualityAdaptationState, mode: BrowserQualityMode, rttMs: Long?, packetsLost: Long?, fps: Double?, targetFps: Int): QualityAdaptationState {
        val constrained = (rttMs ?: 0) > 180 || (packetsLost ?: 0) > 15 || (fps ?: targetFps.toDouble()) < targetFps * 0.85
        return if (constrained) QualityAdaptationState(0, state.constrainedSamples + 1) else QualityAdaptationState(state.healthySamples + 1, 0)
    }
}

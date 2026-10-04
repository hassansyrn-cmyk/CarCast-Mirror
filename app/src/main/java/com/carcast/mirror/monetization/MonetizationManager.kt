package com.carcast.mirror.monetization

import android.app.Activity
import com.carcast.mirror.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Central policy boundary: advertising never participates in casting or receiver flows. */
data class MonetizationUiState(
    val consentReady: Boolean = false,
    val adsReady: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val castingActive: Boolean = false,
    val message: String? = null
)

enum class AdSurface { IDLE_HOME, IDLE_DEVICES }

object MonetizationManager {
    private val _ui = MutableStateFlow(MonetizationUiState())
    val ui: StateFlow<MonetizationUiState> = _ui.asStateFlow()

    private var consentInformation: ConsentInformation? = null
    private var activity: Activity? = null
    private var initialized = false
    private var adsInitialized = false

    val bannerAdUnitId: String
        get() = BuildConfig.ADMOB_BANNER_AD_UNIT_ID

    fun initialize(host: Activity) {
        if (initialized) return
        initialized = true
        activity = host
        consentInformation = UserMessagingPlatform.getConsentInformation(host)
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation?.requestConsentInfoUpdate(
            host,
            parameters,
            {
                updatePrivacyOptionsState()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(host) {
                    updatePrivacyOptionsState()
                    finishConsent(host)
                }
            },
            { error ->
                updatePrivacyOptionsState()
                _ui.value = _ui.value.copy(message = "Advertising consent unavailable; casting remains available")
                finishConsent(host)
            }
        )
    }

    private fun finishConsent(host: Activity) {
        val consent = consentInformation ?: return
        val canRequest = consent.canRequestAds()
        _ui.value = _ui.value.copy(consentReady = true, adsReady = canRequest && BuildConfig.ADS_CONFIGURED, message = if (canRequest) null else "Ads are unavailable")
        if (canRequest && BuildConfig.ADS_CONFIGURED && !adsInitialized) {
            adsInitialized = true
            MobileAds.initialize(host) {}
        }
    }

    private fun updatePrivacyOptionsState() {
        val required = consentInformation?.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        _ui.value = _ui.value.copy(privacyOptionsRequired = required)
    }

    fun setCastingActive(active: Boolean) {
        _ui.value = _ui.value.copy(castingActive = active)
    }

    fun canShowBanner(surface: AdSurface): Boolean =
        _ui.value.adsReady && !_ui.value.castingActive && surface in setOf(AdSurface.IDLE_HOME, AdSurface.IDLE_DEVICES)

    fun showPrivacyOptions(host: Activity, onComplete: (String?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(host) { error ->
            updatePrivacyOptionsState()
            val consent = consentInformation
            _ui.value = _ui.value.copy(adsReady = consent?.canRequestAds() == true && BuildConfig.ADS_CONFIGURED)
            onComplete(error?.message)
        }
    }
}

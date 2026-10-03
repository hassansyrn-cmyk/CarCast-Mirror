package com.carcast.mirror.core

import android.content.Context
import android.content.SharedPreferences

const val STAGE_BROWSER_CONNECTED = "BROWSER_CONNECTED"
const val STAGE_BROWSER_APPROVAL_REQUIRED = "BROWSER_APPROVAL_REQUIRED"
const val STAGE_BROWSER_APPROVE_CLICKED = "BROWSER_APPROVE_CLICKED"
const val STAGE_PROJECTION_CONSENT_LAUNCHED = "PROJECTION_CONSENT_LAUNCHED"
const val STAGE_PROJECTION_RESULT_OK = "PROJECTION_RESULT_OK"
const val STAGE_PROJECTION_RESULT_CANCELLED = "PROJECTION_RESULT_CANCELLED"
const val STAGE_FOREGROUND_SERVICE_START_REQUESTED = "FOREGROUND_SERVICE_START_REQUESTED"
const val STAGE_FOREGROUND_SERVICE_CREATED = "FOREGROUND_SERVICE_CREATED"
const val STAGE_START_FOREGROUND_OK = "START_FOREGROUND_OK"
const val STAGE_WEBRTC_SESSION_CREATED = "WEBRTC_SESSION_CREATED"
const val STAGE_SCREEN_CAPTURER_CREATED = "SCREEN_CAPTURER_CREATED"
const val STAGE_SCREEN_CAPTURE_START_REQUESTED = "SCREEN_CAPTURE_START_REQUESTED"
const val STAGE_SCREEN_CAPTURE_STARTED = "SCREEN_CAPTURE_STARTED"
const val STAGE_OFFER_CREATED = "OFFER_CREATED"
const val STAGE_REMOTE_ANSWER_SET = "REMOTE_ANSWER_SET"
const val STAGE_ICE_CONNECTING = "ICE_CONNECTING"
const val STAGE_ICE_CONNECTED = "ICE_CONNECTED"
const val STAGE_VIDEO_TRACK_STARTED = "VIDEO_TRACK_STARTED"

data class BrowserDebugReport(
    val lastSuccessfulStage: String? = null,
    val lastErrorStage: String? = null,
    val exceptionClass: String? = null,
    val message: String? = null,
    val stackTrace: String? = null,
    val previousCrash: Boolean = false
) {
    fun asText(): String = buildString {
        appendLine("CarCast Browser Receiver Debug")
        appendLine("Last successful stage: ${lastSuccessfulStage ?: "Unavailable"}")
        appendLine("Last error stage: ${lastErrorStage ?: "None"}")
        appendLine("Exception: ${exceptionClass ?: "None"}")
        appendLine("Message: ${message ?: "None"}")
        if (previousCrash) appendLine("Previous CarCast session crashed.")
    }
}

object DebugDiagnostics {
    private const val PREFS = "carcast_browser_debug"
    private const val KEY_REPORT = "report"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        val raw = prefs?.getString(KEY_REPORT, null) ?: return
        val parts = raw.split("\u0001", limit = 6)
        if (parts.size >= 6) AppState.setDebug(BrowserDebugReport(parts[0].nullIfEmpty(), parts[1].nullIfEmpty(), parts[2].nullIfEmpty(), parts[3].nullIfEmpty(), null, parts[5] == "1"))
    }

    fun stage(stage: String) {
        val current = AppState.debug.value
        save(current.copy(lastSuccessfulStage = stage, previousCrash = false))
    }

    fun error(stage: String, throwable: Throwable) {
        val safeMessage = sanitize(throwable.message?.take(500) ?: throwable::class.java.simpleName)
        save(AppState.debug.value.copy(lastErrorStage = stage, exceptionClass = throwable::class.java.name, message = safeMessage, stackTrace = null, previousCrash = false))
    }

    fun uncaught(throwable: Throwable) {
        val safeMessage = sanitize(throwable.message?.take(500) ?: throwable::class.java.simpleName)
        save(AppState.debug.value.copy(lastErrorStage = "UNCAUGHT_EXCEPTION", exceptionClass = throwable::class.java.name, message = safeMessage, stackTrace = null, previousCrash = true))
    }

    fun clear() { prefs?.edit()?.remove(KEY_REPORT)?.apply(); AppState.setDebug(BrowserDebugReport()) }
    fun beginSession() { clear() }
    fun report(): String = AppState.debug.value.asText()

    private fun save(report: BrowserDebugReport) {
        AppState.setDebug(report)
        prefs?.edit()?.putString(KEY_REPORT, listOf(report.lastSuccessfulStage.orEmpty(), report.lastErrorStage.orEmpty(), report.exceptionClass.orEmpty(), report.message.orEmpty(), report.stackTrace.orEmpty(), if (report.previousCrash) "1" else "0").joinToString("\u0001"))?.apply()
    }

    private fun sanitize(message: String): String = message
        .replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "<local-address>")
        .replace(Regex("\\b\\d{6}\\b"), "<verification-code>")

    private fun String.nullIfEmpty() = takeIf { it.isNotEmpty() }
}

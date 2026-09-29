package com.carcast.mirror.core

class BrowserStartupController {
    enum class Phase { IDLE, STARTING, HTTP_BOUND, SIGNALING_BOUND, WAITING_FOR_BROWSER, FAILED }
    var phase: Phase = Phase.IDLE
        private set
    var error: String? = null
        private set

    fun start() { phase = Phase.STARTING; error = null }
    fun httpBound(port: Int) { require(port > 0); phase = Phase.HTTP_BOUND }
    fun signalingBound(port: Int) { require(port > 0); phase = Phase.SIGNALING_BOUND }
    fun ready() { require(phase == Phase.SIGNALING_BOUND); phase = Phase.WAITING_FOR_BROWSER }
    fun failed(message: String) { phase = Phase.FAILED; error = message }
}

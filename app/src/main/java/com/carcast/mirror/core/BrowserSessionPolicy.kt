package com.carcast.mirror.core

import java.security.SecureRandom

enum class BrowserApproval { WAITING, APPROVAL_REQUIRED, APPROVED, REJECTED, EXPIRED }
class BrowserSessionPolicy(private val now: () -> Long = { System.currentTimeMillis() }) {
    val token: String = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private val expiresAt = now() + 120_000
    var approval: BrowserApproval = BrowserApproval.WAITING; private set
    private var client: String? = null
    fun authorize(candidate: String): Boolean { if (now() >= expiresAt) { approval = BrowserApproval.EXPIRED; return false }; if (client != null && client != candidate) return false; client = candidate; approval = BrowserApproval.APPROVAL_REQUIRED; return true }
    fun approve(allow: Boolean): Boolean { if (now() >= expiresAt) { approval = BrowserApproval.EXPIRED; return false }; approval = if (allow) BrowserApproval.APPROVED else BrowserApproval.REJECTED; return allow }
    fun valid(candidate: String) = now() < expiresAt && client == candidate && approval == BrowserApproval.APPROVED
    fun expiresAtMs() = expiresAt
}

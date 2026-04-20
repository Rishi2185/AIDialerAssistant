package com.aicallshield.service

import com.aicallshield.data.model.RiskLevel
import com.aicallshield.util.Constants

/**
 * Lightweight on-device screening heuristics used when cloud AI is unavailable.
 */
data class LocalScreeningResult(
    val spamScore: Float,
    val riskLevel: RiskLevel,
    val matchedKeywords: List<String>,
    val shouldAlert: Boolean,
    val alertMessage: String?,
    val replyText: String,
)

class LocalScreeningEngine {

    private val criticalKeywords = listOf(
        "otp", "one time password", "cvv", "pin", "bank account", "card number",
        "verify account", "kyc", "transfer money", "wire transfer", "gift card",
        "bitcoin", "crypto", "lottery", "prize", "arrest", "legal notice",
    )

    private val warningKeywords = listOf(
        "urgent", "immediately", "claim", "refund", "insurance", "loan",
        "winner", "blocked", "suspended", "payment", "offer", "act now",
    )

    private val otpLikeDigits = Regex("\\b\\d{4,6}\\b")

    fun evaluate(callerText: String, callerNumber: String? = null): LocalScreeningResult {
        val lower = callerText.lowercase()
        val matchedCritical = criticalKeywords.filter { lower.contains(it) }
        val matchedWarning = warningKeywords.filter { lower.contains(it) }
        val matchedKeywords = (matchedCritical + matchedWarning).distinct()

        var score = 0f
        score += matchedCritical.size * 0.30f
        score += matchedWarning.size * 0.12f

        if ((lower.contains("otp") || lower.contains("pin")) && otpLikeDigits.containsMatchIn(lower)) {
            score += 0.22f
        }

        if (lower.contains("urgent") && lower.contains("payment")) {
            score += 0.14f
        }

        if (callerNumber != null && callerNumber.length < 7) {
            score += 0.10f
        }

        score = score.coerceIn(0f, 1f)

        val riskLevel = when {
            score >= Constants.SPAM_HIGH_THRESHOLD -> RiskLevel.CRITICAL
            score >= Constants.SPAM_MEDIUM_THRESHOLD -> RiskLevel.HIGH
            score >= Constants.SPAM_LOW_THRESHOLD -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val shouldAlert = riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL
        val alertMessage = if (shouldAlert) {
            "Suspicious patterns detected. Avoid sharing OTP, PIN, bank, or payment details."
        } else {
            null
        }

        return LocalScreeningResult(
            spamScore = score,
            riskLevel = riskLevel,
            matchedKeywords = matchedKeywords,
            shouldAlert = shouldAlert,
            alertMessage = alertMessage,
            replyText = buildReply(riskLevel),
        )
    }

    fun extractKeywords(text: String): List<String> {
        val lower = text.lowercase()
        return (criticalKeywords + warningKeywords)
            .filter { lower.contains(it) }
            .distinct()
    }

    private fun buildReply(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.CRITICAL,
            RiskLevel.HIGH -> "For security reasons, this line cannot share OTP, payment, or account details. Please contact the user through an official verified channel."

            RiskLevel.MEDIUM -> "Thanks for calling. Please share your full name, organization, and reason for the call. Sensitive information is not shared on this line."
            RiskLevel.LOW -> "Hello. This call is being screened by AICallShield. Please share your name and purpose of the call."
        }
    }
}

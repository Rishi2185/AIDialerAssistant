package com.aicallshield.service

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import android.content.Intent
import android.util.Log

/**
 * Android CallScreeningService that intercepts incoming calls.
 *
 * This service is triggered by the system when an incoming call arrives.
 * It determines whether the call should be screened by AI and coordinates
 * with [AIInCallService] for actual call control (answer/reject/end).
 *
 * Requirements:
 * - App must be set as default call screening app in Settings
 * - Android 10+ (API 29+)
 */
class AICallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "AICallScreening"

        // Shared state for communication with UI
        @Volatile
        var currentCallNumber: String? = null
            private set

        @Volatile
        var isScreening: Boolean = false
            private set

        // Listener for call events
        var callEventListener: CallEventListener? = null
    }

    interface CallEventListener {
        fun onIncomingCall(number: String)
        fun onCallAnswered()
        fun onCallEnded()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle: Uri? = callDetails.handle
        val phoneNumber = handle?.schemeSpecificPart ?: "Unknown"
        val callerName = callDetails.callerDisplayName ?: ""

        Log.i(TAG, "Incoming call from: $phoneNumber ($callerName)")

        // Check if number is in contacts (simplified check)
        val isUnknown = isUnknownNumber(phoneNumber)

        if (isUnknown) {
            Log.i(TAG, "Unknown number detected. Initiating AI screening.")

            currentCallNumber = phoneNumber
            isScreening = true

            // Tell AIInCallService to auto-answer the next incoming call
            AIInCallService.shouldAutoAnswer = true

            // Notify listener (UI/ViewModel)
            callEventListener?.onIncomingCall(phoneNumber)

            // Launch screening activity
            launchScreeningUI(phoneNumber)

            // Allow the call through — AIInCallService will auto-answer it
            // and begin AI screening with TTS.
            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(true)  // Silence the ringtone, let AI handle
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            respondToCall(callDetails, response)

        } else {
            Log.i(TAG, "Known contact. Allowing call normally.")

            // Don't auto-answer known contacts
            AIInCallService.shouldAutoAnswer = false

            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            respondToCall(callDetails, response)
        }
    }

    /**
     * Check if the number is not in the user's contacts.
     * Simplified — in production, query ContactsContract.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun isUnknownNumber(phoneNumber: String): Boolean {
        // Prototype behavior: screen all incoming calls through AI.
        // Contact-provider lookup can be added later for richer filtering.
        return true
    }

    /**
     * Launch the call screening UI activity.
     */
    private fun launchScreeningUI(phoneNumber: String) {
        try {
            val intent = Intent(this, Class.forName("com.aicallshield.ui.CallScreeningActivity")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("caller_number", phoneNumber)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch screening UI", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentCallNumber = null
        isScreening = false
    }
}

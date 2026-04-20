package com.aicallshield.service

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.aicallshield.AICallShieldApp

/**
 * InCallService — gives the app full control over active phone calls.
 *
 * Android requires the app to hold the default-dialer role for this
 * service to be bound by the system.  Once bound, the system delivers
 * every incoming and outgoing [Call] object, allowing the app to:
 *   • answer a ringing call,
 *   • reject / disconnect a call,
 *   • observe call-state transitions (ringing → active → disconnected).
 *
 * For unknown callers the service auto-answers and speaks the AI
 * screening greeting through TTS.
 */
class AIInCallService : InCallService() {

    companion object {
        private const val TAG = "AIInCallService"

        /**
         * The currently active [Call] managed by this service.
         * Null when no call is in progress.
         */
        @Volatile
        var currentCall: Call? = null
            private set

        /**
         * Listener for propagating call lifecycle events to the UI layer
         * (typically the ViewModel).
         */
        @Volatile
        var callLifecycleListener: CallLifecycleListener? = null

        /**
         * Whether the AI should auto-answer the next incoming call.
         * Set to true by [AICallScreeningService] when it detects an unknown number.
         */
        @Volatile
        var shouldAutoAnswer: Boolean = true

        // ── Public call-control methods ────────────────────────────

        /** Answer the currently ringing call. */
        fun answerCurrentCall() {
            currentCall?.let { call ->
                if (call.state == Call.STATE_RINGING) {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    Log.i(TAG, "Call answered programmatically.")
                } else {
                    Log.w(TAG, "answerCurrentCall: call is not ringing (state=${call.state})")
                }
            } ?: Log.w(TAG, "answerCurrentCall: no current call")
        }

        /** Reject the currently ringing call (sends to voicemail). */
        fun rejectCurrentCall() {
            currentCall?.let { call ->
                if (call.state == Call.STATE_RINGING) {
                    call.reject(false, null)
                    Log.i(TAG, "Call rejected programmatically.")
                } else {
                    call.disconnect()
                    Log.i(TAG, "Call disconnected (was not ringing).")
                }
            } ?: Log.w(TAG, "rejectCurrentCall: no current call")
        }

        /** Disconnect / hang up the current call regardless of state. */
        fun endCurrentCall() {
            currentCall?.let { call ->
                call.disconnect()
                Log.i(TAG, "Call disconnected programmatically.")
            } ?: Log.w(TAG, "endCurrentCall: no current call")
        }

        /** Whether there is an active (or ringing) call right now. */
        fun hasActiveCall(): Boolean {
            val state = currentCall?.state ?: return false
            return state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING
        }
    }

    // ── Lifecycle interface ────────────────────────────────────────

    interface CallLifecycleListener {
        fun onCallRinging(number: String)
        fun onCallAnswered(number: String)
        fun onCallEnded(number: String)
    }

    // ── Call callback ──────────────────────────────────────────────

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            val number = extractNumber(call)
            Log.i(TAG, "Call state changed → ${stateToString(state)} ($number)")

            when (state) {
                Call.STATE_RINGING -> {
                    callLifecycleListener?.onCallRinging(number)
                }

                Call.STATE_ACTIVE -> {
                    callLifecycleListener?.onCallAnswered(number)
                    // Speak AI greeting once the call is active
                    speakGreeting()
                }

                Call.STATE_DISCONNECTED -> {
                    callLifecycleListener?.onCallEnded(number)
                    currentCall?.unregisterCallback(this)
                    currentCall = null
                }
            }
        }
    }

    // ── InCallService callbacks ────────────────────────────────────

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val number = extractNumber(call)
        Log.i(TAG, "onCallAdded: $number  state=${stateToString(call.state)}")

        currentCall = call
        call.registerCallback(callCallback)

        if (call.state == Call.STATE_RINGING) {
            callLifecycleListener?.onCallRinging(number)
            launchScreeningUI(number)

            if (shouldAutoAnswer) {
                // Small delay so the screening UI has time to start
                android.os.Handler(mainLooper).postDelayed({
                    if (currentCall?.state == Call.STATE_RINGING) {
                        call.answer(VideoProfile.STATE_AUDIO_ONLY)
                        Log.i(TAG, "Auto-answered unknown call from $number")
                    }
                }, 500L)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val number = extractNumber(call)
        Log.i(TAG, "onCallRemoved: $number")

        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            callLifecycleListener?.onCallEnded(number)
            currentCall = null
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun extractNumber(call: Call): String {
        return call.details?.handle?.schemeSpecificPart ?: "Unknown"
    }

    private fun launchScreeningUI(phoneNumber: String) {
        try {
            val intent = Intent(this, Class.forName("com.aicallshield.ui.CallScreeningActivity")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("caller_number", phoneNumber)
                putExtra("source", "incall_service")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch screening UI", e)
        }
    }

    private fun speakGreeting() {
        try {
            CallSpeechEngine.speak(
                applicationContext,
                "Hello. This call is being screened by A.I. Call Shield. Please state your name and purpose of the call."
            )
        } catch (e: Exception) {
            Log.e(TAG, "TTS greeting failed", e)
        }
    }

    private fun stateToString(state: Int): String = when (state) {
        Call.STATE_NEW -> "NEW"
        Call.STATE_RINGING -> "RINGING"
        Call.STATE_DIALING -> "DIALING"
        Call.STATE_ACTIVE -> "ACTIVE"
        Call.STATE_HOLDING -> "HOLDING"
        Call.STATE_DISCONNECTED -> "DISCONNECTED"
        Call.STATE_CONNECTING -> "CONNECTING"
        Call.STATE_DISCONNECTING -> "DISCONNECTING"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
        Call.STATE_SIMULATED_RINGING -> "SIMULATED_RINGING"
        Call.STATE_AUDIO_PROCESSING -> "AUDIO_PROCESSING"
        else -> "UNKNOWN($state)"
    }

    override fun onDestroy() {
        super.onDestroy()
        currentCall?.unregisterCallback(callCallback)
        currentCall = null
        Log.i(TAG, "AIInCallService destroyed")
    }
}

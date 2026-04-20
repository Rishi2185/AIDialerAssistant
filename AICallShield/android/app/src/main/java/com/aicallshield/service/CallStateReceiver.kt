package com.aicallshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Fallback BroadcastReceiver that listens for phone-state changes.
 *
 * This provides a safety net for detecting incoming calls even when
 * the app is **not** the default dialer (in which case [AIInCallService]
 * won't receive calls).
 *
 * It launches the screening UI when a call starts ringing and
 * notifies when the call ends.
 */
class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"

        @Volatile
        var lastState: String = TelephonyManager.EXTRA_STATE_IDLE

        /** Optional listener for components that want to react to raw telephony events. */
        var telephonyListener: TelephonyEventListener? = null
    }

    interface TelephonyEventListener {
        fun onRinging(number: String?)
        fun onOffHook()
        fun onIdle()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        // Guard against duplicate broadcasts for the same state
        if (state == lastState) return
        lastState = state

        Log.i(TAG, "Phone state: $state  number=$incomingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                telephonyListener?.onRinging(incomingNumber)

                // If the InCallService is NOT active (app is not default dialer),
                // launch the screening UI ourselves as a fallback.
                if (!AIInCallService.hasActiveCall() && incomingNumber != null) {
                    launchScreeningUI(context, incomingNumber)
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                telephonyListener?.onOffHook()
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                telephonyListener?.onIdle()
            }
        }
    }

    private fun launchScreeningUI(context: Context, phoneNumber: String) {
        try {
            val intent = Intent(context, Class.forName("com.aicallshield.ui.CallScreeningActivity")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("caller_number", phoneNumber)
                putExtra("source", "broadcast_receiver")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch screening UI from receiver", e)
        }
    }
}

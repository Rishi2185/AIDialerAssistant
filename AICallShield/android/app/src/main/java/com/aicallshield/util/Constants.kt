package com.aicallshield.util

/**
 * App-wide constants.
 */
object Constants {
    // Backend API
    const val BASE_URL = "http://192.168.1.9:8000/"  // Emulator → localhost
    const val WS_BASE_URL = "ws://192.168.1.9:8000/"
    const val API_PREFIX = "api/v1/"

    // WebSocket
    const val WS_CALL_PATH = "ws/call/"

    // Notification
    const val SCREENING_NOTIFICATION_ID = 1001

    // Preferences
    const val PREFS_NAME = "aicallshield_prefs"
    const val PREF_AI_ENABLED = "ai_screening_enabled"
    const val PREF_AUTO_BLOCK_SPAM = "auto_block_spam"
    const val PREF_SPAM_THRESHOLD = "spam_threshold"
    const val PREF_TTS_VOICE = "tts_voice"
    const val PREF_USER_ID = "user_id"
    const val PREF_FORWARDING_TWILIO_NUMBER = "forwarding_twilio_number"

    // Spam risk thresholds
    const val SPAM_LOW_THRESHOLD = 0.35f
    const val SPAM_MEDIUM_THRESHOLD = 0.6f
    const val SPAM_HIGH_THRESHOLD = 0.8f

    // Audio
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_CHANNEL_CONFIG = 1 // Mono
    const val AUDIO_ENCODING = 16 // PCM 16-bit
}

package com.relay.app.event

import java.net.URI

/** A subscription's address (#33): https only, webcal:// read as https://. */
object SubscriptionUrl {
    private const val MAX_LENGTH = 2000

    /** The address to fetch, or null when it isn't a usable https one. */
    fun normalize(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        val https = when {
            trimmed.startsWith("webcal://", ignoreCase = true) -> "https://" + trimmed.substring("webcal://".length)
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> return null
        }
        val uri = runCatching { URI(https) }.getOrNull() ?: return null
        return https.takeIf { !uri.host.isNullOrBlank() }
    }
}

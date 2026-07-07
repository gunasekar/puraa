package com.puraa.envelope

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders an SMS as the plaintext message the relay sends. The same text
 * goes to whichever destination (Telegram / Discord) is active.
 *
 *   Device: <relay name>
 *   From:   <sender id>
 *   At:     <date, time>
 *
 *   <body>
 */
object Envelope {

    data class SmsPayload(
        val deviceName: String,
        val sender: String,
        val body: String,
        val ts: Long,
    )

    /** Render an SMS as the plaintext envelope. */
    fun encodePlaintext(payload: SmsPayload): String {
        val device = payload.deviceName.ifBlank { "unnamed-device" }
        val sender = payload.sender.ifBlank { "unknown" }
        val at = HUMAN_DATE.format(Date(payload.ts))
        return buildString {
            append("Device: ").append(device).append('\n')
            append("From:   ").append(sender).append('\n')
            append("At:     ").append(at).append('\n')
            append('\n')
            append(payload.body)
        }
    }

    private val HUMAN_DATE: SimpleDateFormat
        get() = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
}

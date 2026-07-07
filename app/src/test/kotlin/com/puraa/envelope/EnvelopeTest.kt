package com.puraa.envelope

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EnvelopeTest {

    @Test
    fun `encodes the SMS details header`() {
        val ts = Calendar.getInstance(TimeZone.getDefault(), Locale.ENGLISH).apply {
            set(2026, Calendar.MAY, 26, 23, 34, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val encoded = Envelope.encodePlaintext(
            Envelope.SmsPayload(
                deviceName = "Mom's Pixel",
                sender = "VK-HDFCBK",
                body = "Rs 1,234.00 debited from A/c XX1234",
                ts = ts,
            ),
        )
        assertTrue(encoded.startsWith("Device: Mom's Pixel\n"))
        assertTrue(encoded.contains("From:   VK-HDFCBK\n"))
        assertTrue(encoded.contains("At:     26 May 2026, 11:34 PM"))
        assertTrue(encoded.endsWith("\n\nRs 1,234.00 debited from A/c XX1234"))
    }

    @Test
    fun `blank device and sender fall back to placeholders`() {
        val encoded = Envelope.encodePlaintext(
            Envelope.SmsPayload(deviceName = "", sender = "  ", body = "hi", ts = 0L),
        )
        assertTrue(encoded.startsWith("Device: unnamed-device\n"))
        assertTrue(encoded.contains("From:   unknown\n"))
    }
}

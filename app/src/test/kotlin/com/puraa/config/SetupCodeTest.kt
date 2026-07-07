package com.puraa.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCodeTest {

    @Test
    fun `telegram code parses without any encoding`() {
        val setup = SetupCode.decode(
            "PURAA-SETUP/1\nbot=123:ABC\nch=-1001234567890\nfilter=HDFCBK,ICICIB",
        )!!
        assertEquals(Destination.TELEGRAM, setup.destination)
        assertTrue(setup.isComplete())
        assertEquals("123:ABC", setup.token)
        assertEquals("-1001234567890", setup.channelId)
        assertEquals("HDFCBK,ICICIB", setup.whitelist)
    }

    @Test
    fun `discord webhook survives verbatim - no url escaping`() {
        val setup = SetupCode.decode(
            "PURAA-SETUP/1\ndiscord=https://discord.com/api/webhooks/1/abc",
        )!!
        assertEquals(Destination.DISCORD, setup.destination)
        assertEquals("https://discord.com/api/webhooks/1/abc", setup.discordWebhook)
    }

    @Test
    fun `tolerates carriage returns and surrounding whitespace`() {
        val setup = SetupCode.decode("  PURAA-SETUP/1\r\nbot=123:ABC\r\nch=-100\r\n")!!
        assertEquals(Destination.TELEGRAM, setup.destination)
        assertEquals("-100", setup.channelId)
    }

    @Test
    fun `codes without the magic header are rejected`() {
        assertNull(SetupCode.decode("bot=123:ABC\nch=-100"))
        assertNull(SetupCode.decode("https://www.padnam.in/puraa/relay?bot=1&ch=2"))
        assertNull(SetupCode.decode("PURAA-SETUP/2\nbot=123:ABC\nch=-100"))
        assertNull(SetupCode.decode(null))
        assertNull(SetupCode.decode(""))
    }

    @Test
    fun `telegram code round-trips through build then decode`() {
        val code = SetupCode.telegram("123:ABC", -1009999, "HDFCBK")
        assertTrue(code.startsWith("PURAA-SETUP/1"))
        assertFalse("must not be a URL", code.startsWith("http"))
        assertFalse("must not be a deep link", code.contains("puraa://"))

        val setup = SetupCode.decode(code)!!
        assertEquals(Destination.TELEGRAM, setup.destination)
        assertEquals("123:ABC", setup.token)
        assertEquals("-1009999", setup.channelId)
        assertEquals("HDFCBK", setup.whitelist)
    }

    @Test
    fun `discord code round-trips and never carries a device name`() {
        val code = SetupCode.discord("https://discord.com/api/webhooks/1/abc", "")
        assertFalse(code.contains("user="))

        val setup = SetupCode.decode(code)!!
        assertEquals(Destination.DISCORD, setup.destination)
        assertEquals("https://discord.com/api/webhooks/1/abc", setup.discordWebhook)
        assertNull(setup.user)
    }

    @Test
    fun `blank filter is omitted from the code`() {
        val code = SetupCode.telegram("123:ABC", -100, "   ")
        assertFalse(code.contains("filter="))
        assertNull(SetupCode.decode(code)!!.whitelist)
    }
}

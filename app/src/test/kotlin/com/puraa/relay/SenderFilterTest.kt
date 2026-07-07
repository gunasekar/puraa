package com.puraa.relay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderFilterTest {

    @Test
    fun `empty whitelist forwards everything`() {
        val filter = SenderFilter("")
        assertTrue(filter.isAllSmsMode)
        assertTrue(filter.shouldForward("HDFCBK"))
        assertTrue(filter.shouldForward("MOM"))
        assertTrue(filter.shouldForward("+919876543210"))
    }

    @Test
    fun `whitelist matches case-insensitively as substring`() {
        val filter = SenderFilter("HDFCBK,ICICIB,CRED")
        assertTrue(filter.shouldForward("HDFCBK"))
        assertTrue(filter.shouldForward("hdfcbk"))
        // Indian operators prefix the sender id with a route code: VK-, AM-, etc.
        assertTrue(filter.shouldForward("VK-HDFCBK"))
        assertTrue(filter.shouldForward("AM-ICICIB-S"))
    }

    @Test
    fun `whitelist rejects unrelated senders`() {
        val filter = SenderFilter("HDFCBK,ICICIB")
        assertFalse(filter.shouldForward("AMAZON"))
        assertFalse(filter.shouldForward("+919876543210"))
        assertFalse(filter.shouldForward(""))
    }

    @Test
    fun `whitelist tolerates whitespace and empty entries`() {
        val filter = SenderFilter("  HDFCBK , , ICICIB ,")
        assertTrue(filter.shouldForward("HDFCBK"))
        assertTrue(filter.shouldForward("ICICIB"))
        assertFalse(filter.isAllSmsMode)
    }
}

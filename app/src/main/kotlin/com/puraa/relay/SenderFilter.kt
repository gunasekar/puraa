package com.puraa.relay

/**
 * Decides which incoming SMS get forwarded.
 *
 * The whitelist is a comma-separated string the child sets at setup
 * time, e.g. `"HDFCBK,ICICIB,CRED"`. Matching is case-insensitive and
 * requires the sender id to *contain* one of the entries — so
 * `"VK-HDFCBK"` (the format Indian banks actually use, with a route
 * prefix) matches an entry of `HDFCBK`.
 *
 * An empty whitelist string means "forward every SMS".
 */
class SenderFilter(whitelistRaw: String) {

    private val tokens: List<String> = whitelistRaw
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.lowercase() }

    val isAllSmsMode: Boolean get() = tokens.isEmpty()

    fun shouldForward(sender: String): Boolean {
        if (isAllSmsMode) return true
        val needle = sender.lowercase()
        return tokens.any { needle.contains(it) }
    }
}

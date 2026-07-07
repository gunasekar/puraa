package com.puraa.config

/**
 * The single place a relay forwards to. Exactly one is active per
 * install — a phone pushes SMS to Telegram *or* Discord, never both.
 */
enum class Destination {
    TELEGRAM,
    DISCORD;

    companion object {
        fun fromName(name: String?): Destination? =
            entries.firstOrNull { it.name == name }
    }
}

package com.puraa.config

/**
 * A relay setup decoded from a scanned Puraa setup code ([SetupCode]). The
 * destination is inferred from which fields are present: a `discord` webhook
 * means Discord, otherwise a `bot` token plus a numeric channel id means
 * Telegram. Exactly one destination, never both.
 *
 * Pure (no Android deps) so it is unit-testable.
 */
internal data class RelaySetup(
    val token: String?,
    val channelId: String?,
    val discordWebhook: String?,
    val user: String?,
    val whitelist: String?,
) {
    /** Discord wins if a webhook is present; otherwise a valid Telegram pair. */
    val destination: Destination? = when {
        !discordWebhook.isNullOrBlank() -> Destination.DISCORD
        !token.isNullOrBlank() && channelId?.trim()?.toLongOrNull() != null -> Destination.TELEGRAM
        else -> null
    }

    /** A setup we can act on carries enough for exactly one destination. */
    fun isComplete(): Boolean = destination != null
}

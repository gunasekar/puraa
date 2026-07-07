package com.puraa.config

/**
 * The payload encoded into a "Share setup" QR code.
 *
 * It is deliberately NOT a URL and NOT a deep link: a generic QR scanner sees
 * only opaque text and can do nothing with it, and there is no dependency on
 * any website or on the OS routing a `puraa://` intent. Only Puraa's own scan
 * flow ([decode]) understands it. The format is a magic header line followed
 * by `key=value` lines — newline-delimited so token/webhook/filter values
 * (which never contain a newline) need no escaping:
 *
 *   PURAA-SETUP/1
 *   bot=<token>
 *   ch=<channel-id>
 *   filter=<csv>            # omitted when empty
 *
 *   PURAA-SETUP/1
 *   discord=<webhook-url>
 *   filter=<csv>            # omitted when empty
 *
 * The device name is intentionally never included — the scanning phone names
 * itself. Pure (no Android deps) so it round-trips in unit tests.
 */
internal object SetupCode {

    /** Magic + version. Bump the version if the field grammar ever changes. */
    private const val HEADER = "PURAA-SETUP/1"

    /** The setup code for the active destination, or null if not configured. */
    fun encode(config: ConfigStore): String? = when (config.relayDestination) {
        Destination.TELEGRAM -> {
            val token = config.relaySenderBotToken
            val channel = config.relayChannelId
            if (token.isNullOrBlank() || channel == null) null
            else telegram(token, channel, config.relaySenderWhitelist)
        }
        Destination.DISCORD -> {
            val webhook = config.relayDiscordWebhookUrl
            if (webhook.isNullOrBlank()) null else discord(webhook, config.relaySenderWhitelist)
        }
        null -> null
    }

    /** Build a Telegram setup code. */
    fun telegram(token: String, channelId: Long, filter: String): String =
        build(listOf("bot=$token", "ch=$channelId"), filter)

    /** Build a Discord setup code. */
    fun discord(webhook: String, filter: String): String =
        build(listOf("discord=$webhook"), filter)

    private fun build(fields: List<String>, filter: String): String {
        val trimmed = filter.trim()
        val all = fields + if (trimmed.isEmpty()) emptyList() else listOf("filter=$trimmed")
        return (listOf(HEADER) + all).joinToString("\n")
    }

    /** Parse a scanned setup code, or null if it isn't a Puraa setup code. */
    fun decode(raw: String?): RelaySetup? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.trim().split("\n").map { it.trim('\r', ' ') }
        if (lines.firstOrNull() != HEADER) return null
        val fields = lines.drop(1).mapNotNull { line ->
            val eq = line.indexOf('=')
            if (eq < 0) null else line.substring(0, eq) to line.substring(eq + 1)
        }.toMap()
        return RelaySetup(
            token = fields["bot"],
            channelId = fields["ch"],
            discordWebhook = fields["discord"],
            user = fields["user"],
            whitelist = fields["filter"],
        )
    }
}

package com.puraa.send

import com.puraa.config.ConfigStore
import com.puraa.config.Destination
import com.puraa.discord.DiscordClient
import com.puraa.telegram.TelegramClient

/**
 * One delivery destination for a rendered message. The relay resolves the
 * single active sink from config and sends every message through it, so
 * the outbox/worker stay destination-agnostic.
 */
interface MessageSink {
    /** Human-facing destination name, for logs and the "configured" notice. */
    val label: String

    /** Deliver [text]; throw on failure so the caller can retry. */
    suspend fun send(text: String)
}

private class TelegramSink(token: String, private val chatId: Long) : MessageSink {
    private val client = TelegramClient(token)
    override val label = "Telegram"
    override suspend fun send(text: String) {
        client.sendMessage(chatId = chatId, text = text)
    }
}

private class DiscordSink(webhookUrl: String) : MessageSink {
    private val client = DiscordClient(webhookUrl)
    override val label = "Discord"
    override suspend fun send(text: String) {
        client.send(text)
    }
}

/** Builds the single active sink from config, or null if not configured. */
object Sinks {
    fun fromConfig(config: ConfigStore): MessageSink? = when (config.relayDestination) {
        Destination.TELEGRAM -> {
            val token = config.relaySenderBotToken
            val chatId = config.relayChannelId
            if (token.isNullOrBlank() || chatId == null) null else TelegramSink(token, chatId)
        }
        Destination.DISCORD -> {
            val url = config.relayDiscordWebhookUrl
            if (url.isNullOrBlank()) null else DiscordSink(url)
        }
        null -> null
    }

    /** Label for the active destination, for status/announcement text. */
    fun labelFor(config: ConfigStore): String = when (config.relayDestination) {
        Destination.TELEGRAM -> "Telegram"
        Destination.DISCORD -> "Discord"
        null -> "(none)"
    }
}

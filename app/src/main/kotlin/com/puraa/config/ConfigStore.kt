package com.puraa.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the relay's secrets and configuration in Android's
 * hardware-backed EncryptedSharedPreferences. The app is relay-only and
 * forwards to exactly one [Destination] — Telegram *or* Discord.
 */
class ConfigStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // --- Destination --------------------------------------------------------

    var relayDestination: Destination?
        get() {
            Destination.fromName(prefs.getString(KEY_RELAY_DESTINATION, null))?.let { return it }
            // Back-compat: installs configured before the destination key
            // existed only ever had Telegram params. Infer from what's set.
            return when {
                !relaySenderBotToken.isNullOrBlank() && relayChannelId != null -> Destination.TELEGRAM
                !relayDiscordWebhookUrl.isNullOrBlank() -> Destination.DISCORD
                else -> null
            }
        }
        set(value) {
            prefs.edit().also { e ->
                if (value == null) e.remove(KEY_RELAY_DESTINATION)
                else e.putString(KEY_RELAY_DESTINATION, value.name)
            }.apply()
        }

    // --- Telegram params ----------------------------------------------------

    var relaySenderBotToken: String?
        get() = prefs.getString(KEY_RELAY_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_RELAY_TOKEN, value).apply() }

    var relayChannelId: Long?
        get() = prefs.getLong(KEY_RELAY_CHANNEL, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
        set(value) {
            prefs.edit().also { e ->
                if (value == null) e.remove(KEY_RELAY_CHANNEL) else e.putLong(KEY_RELAY_CHANNEL, value)
            }.apply()
        }

    // --- Discord params -----------------------------------------------------

    var relayDiscordWebhookUrl: String?
        get() = prefs.getString(KEY_RELAY_DISCORD, null)
        set(value) { prefs.edit().putString(KEY_RELAY_DISCORD, value).apply() }

    // --- Common -------------------------------------------------------------

    var relayDeviceName: String
        get() = prefs.getString(KEY_RELAY_DEVICE_NAME, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_RELAY_DEVICE_NAME, value).apply() }

    /** Comma-separated sender ids for the whitelist filter. Empty = all-SMS. */
    var relaySenderWhitelist: String
        get() = prefs.getString(KEY_RELAY_WHITELIST, "") ?: ""
        set(value) { prefs.edit().putString(KEY_RELAY_WHITELIST, value).apply() }

    // --- Destination-exclusive setters --------------------------------------

    /** Configure Telegram as the destination, clearing any Discord config. */
    fun setTelegram(token: String, channelId: Long) {
        prefs.edit()
            .putString(KEY_RELAY_DESTINATION, Destination.TELEGRAM.name)
            .putString(KEY_RELAY_TOKEN, token)
            .putLong(KEY_RELAY_CHANNEL, channelId)
            .remove(KEY_RELAY_DISCORD)
            .putBoolean(KEY_RELAY_ACTIVE, true)
            .apply()
    }

    /** Configure Discord as the destination, clearing any Telegram config. */
    fun setDiscord(webhookUrl: String) {
        prefs.edit()
            .putString(KEY_RELAY_DESTINATION, Destination.DISCORD.name)
            .putString(KEY_RELAY_DISCORD, webhookUrl)
            .remove(KEY_RELAY_TOKEN)
            .remove(KEY_RELAY_CHANNEL)
            .putBoolean(KEY_RELAY_ACTIVE, true)
            .apply()
    }

    /**
     * Whether the relay is actively forwarding. Distinct from
     * [isRelayConfigured]: "Stop" sets this false but *keeps* the values, so
     * an accidental stop can be undone by re-saving the pre-filled form
     * instead of re-entering the token/webhook. Defaults true so an install
     * configured before this flag existed keeps running.
     */
    var relayActive: Boolean
        get() = prefs.getBoolean(KEY_RELAY_ACTIVE, true)
        set(value) { prefs.edit().putBoolean(KEY_RELAY_ACTIVE, value).apply() }

    fun isRelayConfigured(): Boolean = when (relayDestination) {
        Destination.TELEGRAM -> !relaySenderBotToken.isNullOrBlank() && relayChannelId != null
        Destination.DISCORD -> !relayDiscordWebhookUrl.isNullOrBlank()
        null -> false
    }

    /** Configured *and* not paused — the gate for actually forwarding SMS. */
    fun isRelayRunning(): Boolean = isRelayConfigured() && relayActive

    fun clearRelay() {
        prefs.edit()
            .remove(KEY_RELAY_DESTINATION)
            .remove(KEY_RELAY_TOKEN)
            .remove(KEY_RELAY_CHANNEL)
            .remove(KEY_RELAY_DISCORD)
            .remove(KEY_RELAY_WHITELIST)
            .remove(KEY_RELAY_DEVICE_NAME)
            .remove(KEY_RELAY_ACTIVE)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "puraa_secure_prefs"
        const val KEY_RELAY_DESTINATION = "relay.destination"
        const val KEY_RELAY_TOKEN = "relay.bot_send_token"
        const val KEY_RELAY_CHANNEL = "relay.channel_id"
        const val KEY_RELAY_DISCORD = "relay.discord_webhook"
        const val KEY_RELAY_WHITELIST = "relay.sender_whitelist"
        const val KEY_RELAY_DEVICE_NAME = "relay.device_name"
        const val KEY_RELAY_ACTIVE = "relay.active"
    }
}

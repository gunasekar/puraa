package com.puraa.relay

import android.content.Context
import android.util.Log
import com.puraa.config.ConfigStore
import com.puraa.send.Sinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Posts a one-off "relay configured" confirmation to the channel right
 * after setup, so whoever configured the phone gets immediate proof in
 * Telegram that this device is live and with which settings. The bot
 * token is never included — the channel already implies it, and it's a
 * secret.
 *
 * The notice goes through the normal outbox + [RelayWorker] path, so it
 * inherits the same retry/offline handling as a forwarded SMS.
 */
object RelayAnnouncer {

    fun announceConfigured(context: Context, config: ConfigStore) {
        val appContext = context.applicationContext
        val filter = config.relaySenderWhitelist
        val text = buildString {
            append("✅ Puraa relay configured\n")
            append("Device: ").append(config.relayDeviceName.ifBlank { "(unnamed)" }).append('\n')
            append("Destination: ").append(Sinks.labelFor(config)).append('\n')
            append("Filter: ").append(if (filter.isBlank()) "All SMS (no filter)" else filter).append('\n')
            append("This phone will now forward matching SMS here.")
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                OutboxRepository(appContext).enqueueNotice(text)
                RelayWorker.enqueue(appContext)
            }.onFailure { Log.w(TAG, "Failed to enqueue configuration notice", it) }
        }
    }

    private const val TAG = "RelayAnnouncer"
}

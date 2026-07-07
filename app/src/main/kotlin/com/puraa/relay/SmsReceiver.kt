package com.puraa.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.puraa.config.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The relay's SMS source. The OS delivers `SMS_RECEIVED` the instant a
 * message arrives — so we only ever see the SMS that just came in, never
 * historical ones. There is no provider query and no "last seen" cursor
 * to keep correct: "forward only what we hear, never old messages" is
 * guaranteed by construction.
 *
 * The receiver does the minimum synchronous work — parse, filter,
 * enqueue — then hands off to [RelayWorker] for the network send. It
 * never touches the network itself (a broadcast handler must return
 * fast, and delivery must survive a flaky connection).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A single SMS can arrive as multiple PDUs (multipart). They share
        // a sender and timestamp; concatenate the bodies back into one.
        val sender = messages.first().displayOriginatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val ts = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        if (body.isBlank()) return

        val config = ConfigStore(context.applicationContext)
        if (!config.isRelayRunning()) return

        if (!SenderFilter(config.relaySenderWhitelist).shouldForward(sender)) {
            Log.d(TAG, "Dropped SMS from '$sender' (filter)")
            return
        }

        val deviceName = config.relayDeviceName
        val appContext = context.applicationContext

        // goAsync keeps the broadcast alive for the short DB write. The
        // network send happens later, in RelayWorker.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                OutboxRepository(appContext).enqueueSms(
                    deviceName = deviceName,
                    sender = sender,
                    body = body,
                    receivedAt = ts,
                )
                RelayWorker.enqueue(appContext)
                Log.i(TAG, "Enqueued SMS from '$sender'")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to enqueue SMS from '$sender'", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}

package com.puraa.relay

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.puraa.config.ConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manual "push last 15 minutes" backfill. Unlike the automatic relay —
 * which only ever sees SMS live via the `SMS_RECEIVED` broadcast — this
 * *queries the SMS inbox* (needs `READ_SMS`) and enqueues everything in
 * the recent window, ignoring the sender filter and without de-duplicating
 * against what may already have been forwarded. It's an explicit, user-
 * initiated "send me everything recent" hammer / catch-up for gaps.
 */
object RecentSmsPush {

    /** How far back a manual push reaches. */
    const val WINDOW_MS = 15 * 60 * 1000L

    /**
     * Enqueue every inbox SMS newer than [WINDOW_MS] ago for delivery.
     * Returns the number enqueued (0 if nothing recent, or not configured).
     */
    suspend fun pushRecent(context: Context): Int = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val config = ConfigStore(appContext)
        if (!config.isRelayRunning()) return@withContext 0

        val since = System.currentTimeMillis() - WINDOW_MS
        val repo = OutboxRepository(appContext)
        val deviceName = config.relayDeviceName

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val cursor = try {
            appContext.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Inbox query failed (READ_SMS missing?)", t)
            return@withContext 0
        } ?: return@withContext 0

        var count = 0
        cursor.use { c ->
            val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(addrIdx).orEmpty()
                val body = c.getString(bodyIdx).orEmpty()
                if (body.isBlank()) continue
                repo.enqueueSms(
                    deviceName = deviceName,
                    sender = sender,
                    body = body,
                    receivedAt = c.getLong(dateIdx),
                )
                count++
            }
        }

        if (count > 0) {
            RelayWorker.enqueue(appContext)
            Log.i(TAG, "Manual push enqueued $count message(s) from the last 15 min")
        }
        count
    }

    private const val TAG = "RecentSmsPush"
}

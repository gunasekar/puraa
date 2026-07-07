package com.puraa.relay

import android.content.Context
import com.puraa.envelope.Envelope
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around [OutboxDao] that also encodes a fresh SMS into
 * an envelope before enqueuing.
 *
 * Retry timing is owned by [RelayWorker]: fast in-process retries for
 * transient blips, then WorkManager's network-constrained 10s backoff.
 * A failed row is left immediately eligible for the next drain.
 */
class OutboxRepository(context: Context) {

    private val dao = AppDatabase.get(context).outbox()

    suspend fun enqueueSms(
        deviceName: String,
        sender: String,
        body: String,
        receivedAt: Long,
    ): Long {
        val envelope = Envelope.encodePlaintext(
            Envelope.SmsPayload(
                deviceName = deviceName,
                sender = sender,
                body = body,
                ts = receivedAt,
            ),
        )
        val row = OutboxEntity(
            receivedAt = receivedAt,
            sender = sender,
            envelopeText = envelope,
            status = OutboxEntity.Status.PENDING,
            attempts = 0,
            lastErrorMessage = null,
            nextAttemptAt = 0L,
        )
        return dao.insert(row)
    }

    /**
     * Enqueue an app-generated notice (e.g. the "relay configured"
     * confirmation) for delivery to the channel through the same reliable
     * path as forwarded SMS.
     */
    suspend fun enqueueNotice(text: String): Long {
        val row = OutboxEntity(
            receivedAt = System.currentTimeMillis(),
            sender = "Puraa",
            envelopeText = text,
            status = OutboxEntity.Status.PENDING,
            attempts = 0,
            lastErrorMessage = null,
            nextAttemptAt = 0L,
        )
        return dao.insert(row)
    }

    suspend fun nextReadyForSend(now: Long): OutboxEntity? = dao.nextReadyForSend(now)

    /** True if any row is still pending or waiting in backoff. */
    suspend fun hasQueued(): Boolean = dao.queuedCountNow() > 0

    suspend fun markSent(id: Long) {
        dao.markSent(id)
        dao.trimTerminal(keep = TERMINAL_HISTORY_KEEP)
    }

    /**
     * Record a failed send. After [MAX_ATTEMPTS] the row is parked as
     * FAILED so it can never head-of-line-block later messages; before that
     * it backs off to a future time, letting the worker drain newer rows in
     * the meantime rather than spinning on this one.
     */
    suspend fun markFailed(id: Long, error: String, attemptsSoFar: Int, now: Long) {
        if (attemptsSoFar >= MAX_ATTEMPTS) {
            dao.markFailed(id, error)
            dao.trimTerminal(keep = TERMINAL_HISTORY_KEEP)
        } else {
            val delayMs = minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl (attemptsSoFar - 1))
            dao.markBackoff(id, error, now + delayMs)
        }
    }

    /** Wipe the queue (on stop/reconfigure) so nothing stale carries over. */
    suspend fun clear() = dao.clearAll()

    fun queuedCount(): Flow<Int> = dao.countQueued()
    fun sentCount(): Flow<Int> = dao.countSent()
    fun recent(limit: Int = RECENT_LIMIT): Flow<List<OutboxEntity>> = dao.recent(limit)

    private companion object {
        /** Rows shown in the "Recent activity" list. */
        const val RECENT_LIMIT = 10

        /** Terminal (sent/failed) rows kept in the DB; older ones reaped. */
        const val TERMINAL_HISTORY_KEEP = 20

        /** Give up on a row after this many failed drains. */
        const val MAX_ATTEMPTS = 6
        const val BASE_BACKOFF_MS = 15_000L
        const val MAX_BACKOFF_MS = 300_000L
    }
}

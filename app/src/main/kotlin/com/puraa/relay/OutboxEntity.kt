package com.puraa.relay

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per SMS we have queued or already sent. Rows with
 * [status] = SENT can be reaped on a schedule (or kept for an audit
 * trail).
 *
 * [envelopeText] is the wire payload — the human-readable plaintext
 * envelope produced by [com.puraa.envelope.Envelope.encodePlaintext],
 * sent verbatim as the Telegram message text.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val receivedAt: Long,
    val sender: String,
    val envelopeText: String,
    val status: Status,
    val attempts: Int,
    val lastErrorMessage: String?,
    val nextAttemptAt: Long,
) {
    enum class Status {
        /** Newly inserted, eligible to send immediately. */
        PENDING,

        /** Failed once or more; [nextAttemptAt] gates the next retry. */
        BACKOFF,

        /** Delivered and acknowledged by the destination. */
        SENT,

        /** Retries exhausted; parked so it can't block later messages. */
        FAILED,
    }
}

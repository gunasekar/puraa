package com.puraa.relay

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(row: OutboxEntity): Long

    /**
     * Next row ready to send: PENDING, or BACKOFF whose retry time
     * has arrived. Ordered FIFO by id so we preserve send order.
     */
    @Query(
        """
        SELECT * FROM outbox
        WHERE status = 'PENDING'
           OR (status = 'BACKOFF' AND nextAttemptAt <= :now)
        ORDER BY id ASC
        LIMIT 1
        """,
    )
    suspend fun nextReadyForSend(now: Long): OutboxEntity?

    @Query(
        """
        UPDATE outbox SET status = 'SENT', attempts = attempts + 1,
                          lastErrorMessage = NULL, nextAttemptAt = 0
        WHERE id = :id
        """,
    )
    suspend fun markSent(id: Long)

    @Query(
        """
        UPDATE outbox SET status = 'BACKOFF', attempts = attempts + 1,
                          lastErrorMessage = :error, nextAttemptAt = :nextAttemptAt
        WHERE id = :id
        """,
    )
    suspend fun markBackoff(id: Long, error: String, nextAttemptAt: Long)

    @Query(
        """
        UPDATE outbox SET status = 'FAILED', attempts = attempts + 1,
                          lastErrorMessage = :error, nextAttemptAt = 0
        WHERE id = :id
        """,
    )
    suspend fun markFailed(id: Long, error: String)

    /** Wipe the queue — used when the relay is stopped/reconfigured. */
    @Query("DELETE FROM outbox")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING' OR status = 'BACKOFF'")
    fun countQueued(): Flow<Int>

    /** One-shot variant of [countQueued] for the send worker. */
    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING' OR status = 'BACKOFF'")
    suspend fun queuedCountNow(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'SENT'")
    fun countSent(): Flow<Int>

    @Query("SELECT * FROM outbox ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<OutboxEntity>>

    /**
     * Cap the terminal (SENT or FAILED) history so neither a long offline
     * period nor a sustained misconfiguration can grow the DB without bound.
     */
    @Query(
        """
        DELETE FROM outbox
        WHERE id IN (
            SELECT id FROM outbox
            WHERE status IN ('SENT', 'FAILED')
            ORDER BY id ASC
            LIMIT max(0, (SELECT COUNT(*) FROM outbox WHERE status IN ('SENT', 'FAILED')) - :keep)
        )
        """,
    )
    suspend fun trimTerminal(keep: Int)
}

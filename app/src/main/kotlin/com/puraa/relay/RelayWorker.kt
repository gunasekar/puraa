package com.puraa.relay

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.puraa.config.ConfigStore
import com.puraa.send.MessageSink
import com.puraa.send.Sinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Drains the outbox to Telegram, then stops. Nothing runs at idle — the
 * worker is scheduled by [SmsReceiver] when an SMS arrives and by
 * WorkManager itself after a retry or reboot. On Android 12+ it runs as
 * an expedited job; on older versions as a brief foreground service.
 *
 * Retry/offline are delegated to WorkManager (network constraint +
 * exponential backoff) so there is no hand-rolled polling loop.
 */
class RelayWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val config = ConfigStore(applicationContext)
        val sink = Sinks.fromConfig(config)
        if (sink == null) {
            // Relay was stopped/reconfigured while queued — drop the work.
            Log.w(TAG, "No destination configured — dropping drain")
            return Result.success()
        }

        val repo = OutboxRepository(applicationContext)

        while (true) {
            val now = System.currentTimeMillis()
            val next = repo.nextReadyForSend(now) ?: break
            val error = trySend(sink, next.envelopeText)
            if (error == null) {
                repo.markSent(next.id)
                Log.i(TAG, "Sent outbox row ${next.id}")
            } else {
                // Fast in-worker retries were exhausted. Park/back off this
                // row (OutboxRepository.markFailed) and keep draining newer
                // ones — one bad message must never block the whole queue.
                Log.w(TAG, "Send failed for row ${next.id} after retries: $error")
                repo.markFailed(next.id, error, attemptsSoFar = next.attempts + 1, now = now)
            }
        }

        // Anything still queued is waiting out a backoff; come back for it.
        return if (repo.hasQueued()) Result.retry() else Result.success()
    }

    /**
     * Send one envelope, retrying transient failures in-process with a
     * short backoff (immediate, +1s, +3s) so a momentary network blip
     * costs ~1s, not WorkManager's coarse 10s+ reschedule. Returns null on
     * success, or the last error message if all attempts failed.
     */
    private suspend fun trySend(
        sink: MessageSink,
        text: String,
    ): String? {
        var lastError: String? = null
        for (delayMs in IN_WORKER_BACKOFF_MS) {
            if (delayMs > 0) delay(delayMs)
            try {
                sink.send(text)
                return null
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t.message ?: t::class.java.simpleName
                Log.w(TAG, "Send attempt failed: $lastError")
            }
        }
        return lastError
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        RelayNotifications.foregroundInfo(applicationContext)

    companion object {
        private const val TAG = "RelayWorker"
        private const val UNIQUE_WORK = "puraa-relay-drain"

        /** In-process retry backoff for transient send failures (ms). */
        private val IN_WORKER_BACKOFF_MS = longArrayOf(0L, 1_000L, 3_000L)

        /**
         * Ensure a drain runs. Uses a network constraint so the send is
         * deferred (not failed) while offline, and expedites the work so
         * a bank SMS lands in Telegram within seconds even under Doze.
         *
         * KEEP (not APPEND) avoids chaining a new SMS behind an in-flight
         * or backing-off drain — the running worker already loops over all
         * ready rows, so the new one is picked up without waiting in line.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RelayWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}

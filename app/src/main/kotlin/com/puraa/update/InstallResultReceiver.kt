package com.puraa.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat

/**
 * Where [PackageInstaller] reports back after [Updater] commits a session.
 *
 * The interesting branch is [PackageInstaller.STATUS_PENDING_USER_ACTION] — the
 * first self-update on any phone, and every update on Android 11 or older. The
 * platform hands back an Intent that shows the install confirmation and leaves
 * it to us to display.
 *
 * Puraa only ever commits a session from the update dialog, so the app is in the
 * foreground when this fires and the Intent can simply be launched. That is the
 * reason the flow is in-app only: a background commit could not show this dialog
 * at all — Android 10+ drops activity starts from a background app — and would
 * have to fall back to a notification the user may never see.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(
                    intent, Intent.EXTRA_INTENT, Intent::class.java,
                )
                if (confirm == null) {
                    Log.e(TAG, "Pending user action with no confirmation intent")
                    UpdateStatus.publish(
                        UpdateStatus.Outcome.Failed(versionName, "Android did not return an install prompt"),
                    )
                    return
                }
                // NEW_TASK because a receiver has no task of its own to launch
                // into. Dropped only if the user backgrounded the app between
                // tapping "Update now" and this broadcast — in which case the
                // next check picks the same release up again.
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onSuccess {
                        UpdateStatus.publish(UpdateStatus.Outcome.AwaitingConfirmation(versionName))
                    }
                    .onFailure {
                        Log.e(TAG, "Confirmation launch failed: ${it.message}")
                        UpdateStatus.publish(
                            UpdateStatus.Outcome.Failed(versionName, "Couldn't open the install prompt"),
                        )
                    }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.e(TAG, "Installed $versionName")
                UpdateStatus.publish(UpdateStatus.Outcome.Succeeded)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e(TAG, "Install of $versionName failed (status $status): $message")
                UpdateStatus.publish(UpdateStatus.Outcome.Failed(versionName, message))
            }
        }
    }

    companion object {
        // Log.e throughout, including for the success case: release builds strip
        // Log.i/w/d (see proguard-rules.pro), and release is the only build
        // where the updater runs at all. Error level is the only level that
        // survives to be diagnosed from `make logcat`.
        private const val TAG = "InstallResult"
        private const val ACTION_INSTALL_STATUS = "com.puraa.action.INSTALL_STATUS"
        private const val EXTRA_VERSION_NAME = "com.puraa.extra.VERSION_NAME"
        private const val REQUEST_CODE = 2001

        /**
         * The [IntentSender] to hand [PackageInstaller.Session.commit].
         *
         * FLAG_MUTABLE is mandatory here: the platform fills in the status and
         * the confirmation Intent as extras, which an immutable PendingIntent
         * would forbid — leaving every install stuck at "pending".
         */
        fun intentSender(context: Context, versionName: String): IntentSender {
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setAction(ACTION_INSTALL_STATUS)
                .putExtra(EXTRA_VERSION_NAME, versionName)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            return PendingIntent
                .getBroadcast(context.applicationContext, REQUEST_CODE, intent, flags)
                .intentSender
        }
    }
}

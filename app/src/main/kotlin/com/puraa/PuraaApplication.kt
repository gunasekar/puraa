package com.puraa

import android.app.Application
import com.puraa.relay.RelayNotifications

class PuraaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The relay has no always-on service; SMS arrival wakes a
        // BroadcastReceiver which schedules a short WorkManager send.
        // We only pre-create the notification channel the worker uses
        // for its brief foreground window on Android < 12.
        RelayNotifications.ensureChannel(this)

        // Nothing is registered here for updates by design: Puraa checks for a
        // new release only when the app is opened (see ui/UpdatePrompt.kt), so
        // there is no background work and no notification channel of its own.
    }
}

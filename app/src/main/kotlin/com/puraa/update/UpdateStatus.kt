package com.puraa.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How [InstallResultReceiver] talks back to the UI.
 *
 * [Updater.downloadAndInstall] can only report that a session was *committed* —
 * what the platform does next arrives asynchronously in a broadcast. Since the
 * update flow is entirely in-app, that outcome has to reach the open dialog, and
 * a plain in-memory flow is enough: the receiver and the UI are always in the
 * same process, and nothing here needs to outlive it. A successful install
 * replaces the process anyway.
 */
object UpdateStatus {

    sealed interface Outcome {
        /** The platform raised its confirmation dialog; waiting on the user. */
        data class AwaitingConfirmation(val versionName: String) : Outcome

        /** Rarely seen — a successful install of our own package kills us. */
        data object Succeeded : Outcome

        data class Failed(val versionName: String, val reason: String?) : Outcome
    }

    private val _outcome = MutableStateFlow<Outcome?>(null)
    val outcome: StateFlow<Outcome?> = _outcome.asStateFlow()

    fun publish(outcome: Outcome) { _outcome.value = outcome }

    /** Called when the update dialog opens, so a stale outcome isn't re-shown. */
    fun reset() { _outcome.value = null }
}

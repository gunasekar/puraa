package com.puraa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.puraa.BuildConfig
import com.puraa.update.UpdateManifest
import com.puraa.update.UpdateStatus
import com.puraa.update.Updater
import kotlinx.coroutines.launch

/**
 * The whole update flow: check, download, verify, install — all of it in the
 * foreground, driven by the user.
 *
 * Puraa does no background update work at all, so this dialog is the only path.
 * Two things follow from that. It can report "you're on the latest release",
 * which a silent background check never could. And because the app is in the
 * foreground when the session is committed, Android's install confirmation
 * opens directly instead of having to arrive as a notification that might be
 * swiped away and lost.
 *
 * Opened either from ⋮ → "Check for updates" (where it checks on open) or from
 * [UpdateAvailableCard] (where the answer is already known — pass [known]).
 */
@Composable
fun UpdateDialog(
    onDismiss: () -> Unit,
    known: UpdateManifest? = null,
) {
    val context = LocalContext.current
    val updater = remember { Updater(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var state by remember {
        mutableStateOf<UpdateState>(
            when {
                !BuildConfig.SELF_UPDATE_ENABLED -> UpdateState.Disabled
                known != null -> UpdateState.Available(known)
                else -> UpdateState.Checking
            },
        )
    }

    // The platform's verdict on a committed session arrives asynchronously.
    val outcome by UpdateStatus.outcome.collectAsState()

    LaunchedEffect(Unit) {
        UpdateStatus.reset()
        if (state !is UpdateState.Checking) return@LaunchedEffect
        state = when (val result = updater.check()) {
            Updater.CheckResult.UpToDate -> UpdateState.UpToDate
            is Updater.CheckResult.Available -> UpdateState.Available(result.manifest)
            is Updater.CheckResult.Failed -> UpdateState.Failed(result.reason)
        }
    }

    // A failed install is only knowable from the receiver; fold it into the UI
    // so a signing-key mismatch doesn't look like a dialog that did nothing.
    LaunchedEffect(outcome) {
        when (val o = outcome) {
            is UpdateStatus.Outcome.Failed ->
                state = UpdateState.Failed(o.reason ?: "the install was rejected")
            is UpdateStatus.Outcome.AwaitingConfirmation ->
                state = UpdateState.AwaitingConfirmation(o.versionName)
            else -> Unit
        }
    }

    val available = state as? UpdateState.Available

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Updates") },
        text = {
            Column {
                Text(
                    "Installed: ${updater.installedVersionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                StateBody(state)
                if (available != null && !updater.canInstallPackages) {
                    Spacer(Modifier.height(12.dp))
                    InstallPermissionHint(onOpenSettings = { openInstallUnknownAppsSettings(context) })
                }
            }
        },
        confirmButton = {
            if (available != null) {
                TextButton(
                    onClick = {
                        val manifest = available.manifest
                        state = UpdateState.Working(manifest.versionName)
                        scope.launch {
                            val result = updater.downloadAndInstall(manifest)
                            if (result is Updater.InstallResult.Failed) {
                                state = UpdateState.Failed(result.reason)
                            }
                            // On Committed, leave the state alone — the receiver
                            // publishes what happened next via UpdateStatus.
                        }
                    },
                ) { Text("Update now") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (available != null || state is UpdateState.Working) {
                // Closing mid-download cancels it and discards the staged
                // session — nothing is left half-installed, and the card
                // reappears next time the app is opened.
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        },
    )
}

@Composable
private fun StateBody(state: UpdateState) {
    when (state) {
        UpdateState.Checking -> Working("Checking GitHub Releases…")

        UpdateState.UpToDate -> Text(
            "You're on the latest release.",
            style = MaterialTheme.typography.bodyLarge,
        )

        is UpdateState.Available -> Column {
            Text(
                "Puraa ${state.manifest.versionName} is available.",
                style = MaterialTheme.typography.bodyLarge,
            )
            state.manifest.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Spacer(Modifier.height(6.dp))
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Android will ask you to confirm the first time Puraa updates " +
                    "itself. After that, updates apply as soon as you tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is UpdateState.Working -> Working("Downloading and verifying ${state.versionName}…")

        is UpdateState.AwaitingConfirmation -> Text(
            "${state.versionName} is verified and staged. Confirm the install " +
                "when Android asks — your relay settings are kept.",
            style = MaterialTheme.typography.bodyMedium,
        )

        is UpdateState.Failed -> Text(
            "Couldn't update: ${state.reason}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        UpdateState.Disabled -> Text(
            "Self-update is off in debug builds — a debug Puraa is a different " +
                "package, signed with a different key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shown alongside an available update when Puraa hasn't been allowed to install
 * apps. Advisory, not a blocker — "Update now" stays enabled, because the
 * install may well succeed anyway. This just puts the fix one tap away instead
 * of leaving the user to guess at an "App not installed" from Android.
 */
@Composable
private fun InstallPermissionHint(onOpenSettings: () -> Unit) {
    Column {
        Text(
            "If Android blocks the install, allow Puraa to install apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onOpenSettings,
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
        ) { Text("Open install settings", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Working(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Disabled : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Working(val versionName: String) : UpdateState
    data class AwaitingConfirmation(val versionName: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

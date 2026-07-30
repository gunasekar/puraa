package com.puraa.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.puraa.BuildConfig
import com.puraa.update.UpdateManifest
import com.puraa.update.Updater
import kotlinx.coroutines.launch

/**
 * Checks for a new release on every app entry point, the way Google's own
 * in-app update guidance prescribes — `ON_RESUME` rather than a background job,
 * so the answer is always fresh for the person looking at the screen.
 *
 * Deliberately **not** cached to disk. A remembered "update available" would
 * survive into situations where it isn't actionable (offline, or already
 * installed by hand), and the check itself is a few hundred bytes of JSON. If
 * the network is down there is nothing to show, because there is nothing that
 * could be installed.
 *
 * Returns null while checking, when up to date, or when the check failed — the
 * card only appears on a definite yes. Failures are the manual dialog's job to
 * report, since that's where the user actually asked a question.
 */
@Composable
fun rememberPendingUpdate(): State<UpdateManifest?> {
    val context = LocalContext.current
    val updater = remember { Updater(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<UpdateManifest?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return@LifecycleEventEffect
        scope.launch {
            pending.value = (updater.check() as? Updater.CheckResult.Available)?.manifest
        }
    }
    return pending
}

/**
 * The standing "there's a new version" surface on the relay screen.
 *
 * It is not dismissible, which is the point: the notification it replaces could
 * be swiped away and lost, leaving no trace that an update was waiting. This
 * card is regenerated from a live check every time the app is opened, so the
 * only way to make it go away is to update.
 */
@Composable
fun UpdateAvailableCard(
    manifest: UpdateManifest,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuraaCard(modifier = modifier.fillMaxWidth(), tone = CardTone.Accent) {
        Eyebrow("Update available", color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(8.dp))
        Text(
            "Puraa ${manifest.versionName}",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            manifest.notes?.takeIf { it.isNotBlank() }
                ?: "You're on ${BuildConfig.VERSION_NAME}. Your relay settings are kept.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = "Update now",
            onClick = onUpdate,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

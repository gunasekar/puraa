package com.puraa.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.puraa.R
import com.puraa.config.ConfigStore
import com.puraa.config.SetupCode
import com.puraa.relay.OutboxEntity
import com.puraa.relay.OutboxRepository
import com.puraa.relay.RecentSmsPush
import com.puraa.relay.RelayWorker
import com.puraa.send.Sinks
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayScreen(
    config: ConfigStore,
    onStopped: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { OutboxRepository(context.applicationContext) }
    val queued by repo.queuedCount().collectAsState(initial = 0)
    val sent by repo.sentCount().collectAsState(initial = 0)
    val recent by repo.recent().collectAsState(initial = emptyList())

    var menuOpen by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val doPush: () -> Unit = {
        scope.launch {
            val n = RecentSmsPush.pushRecent(context)
            Toast.makeText(
                context,
                if (n > 0) "Pushing $n message(s) from the last 15 min…"
                else "No SMS in the last 15 minutes.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val readSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            doPush()
        } else {
            Toast.makeText(
                context,
                "SMS read access is needed to push recent messages.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { PuraaWordmark() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Share this setup") },
                            onClick = {
                                menuOpen = false
                                showQr = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Stop and reconfigure") },
                            onClick = {
                                menuOpen = false
                                confirmStop = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            RelayStatusCard(destination = Sinks.labelFor(config))

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(label = "Queued", value = queued.toString(), modifier = Modifier.weight(1f))
                StatTile(label = "Sent", value = sent.toString(), modifier = Modifier.weight(1f), accent = true)
            }

            Spacer(Modifier.height(20.dp))

            val smsGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECEIVE_SMS,
            ) == PackageManager.PERMISSION_GRANTED

            InfoCard(
                deviceName = config.relayDeviceName.ifBlank { "(unnamed)" },
                whitelistSummary = config.relaySenderWhitelist.ifBlank { "All SMS (no filter)" },
                smsGranted = smsGranted,
            )

            if (!smsGranted) {
                Spacer(Modifier.height(12.dp))
                RestrictedSmsHint(onOpenSettings = { openAppSettings(context) })
            }

            Spacer(Modifier.height(20.dp))

            TonalButton(
                text = "Push last 15 minutes",
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_SMS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) doPush() else readSmsLauncher.launch(Manifest.permission.READ_SMS)
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            Text(
                text = "Forwards every SMS from the last 15 minutes, ignoring the filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(20.dp))

            Text("Recent activity", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (recent.isEmpty()) {
                Text(
                    text = "Nothing yet. SMS will appear here as they arrive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    items(recent) { row ->
                        OutboxRow(row)
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop relay?") },
            text = {
                val who = config.relayDeviceName.ifBlank { "this phone" }
                Text(
                    "SMS from $who will stop forwarding to ${Sinks.labelFor(config)} " +
                        "until you start the relay again. Your settings are kept, so " +
                        "you can restart without re-entering them.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStop = false
                        RelayWorker.cancel(context)
                        scope.launch { repo.clear() }
                        // Pause, don't wipe: keep the token/webhook/filter so
                        // the setup form re-fills them if this was a mistake.
                        config.relayActive = false
                        onStopped()
                    },
                ) { Text("Yes, stop", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("Cancel") }
            },
        )
    }

    if (showQr) {
        val code = remember { SetupCode.encode(config) }
        val qr = remember(code) {
            code?.let {
                runCatching {
                    BarcodeEncoder().encodeBitmap(it, BarcodeFormat.QR_CODE, 720, 720)
                }.getOrNull()
            }
        }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Share this setup") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (qr != null) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Setup QR code",
                            modifier = Modifier.size(220.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Scan this on another phone's setup screen to copy this " +
                                "relay. It contains your token/webhook — show it only to " +
                                "phones you trust.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Nothing to share yet.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val content = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val labelColor = if (accent) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    PuraaCard(
        modifier = modifier,
        tone = if (accent) CardTone.Accent else CardTone.Neutral,
        contentPadding = PaddingValues(20.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = content)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
    }
}

/**
 * Shown when SMS access isn't granted. Beyond a plain "grant permission"
 * nudge, it spells out the Android 13+ *restricted settings* trap: a build
 * sideloaded from a browser has its SMS toggle greyed out, and the only way
 * through is "Allow restricted settings" from the app's own settings page —
 * which most users never find on their own.
 */
@Composable
private fun RestrictedSmsHint(onOpenSettings: () -> Unit) {
    PuraaCard(
        modifier = Modifier.fillMaxWidth(),
        tone = CardTone.Alert,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "SMS access is off — nothing will forward",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "If the SMS toggle looks greyed out, Android is blocking it as a " +
                "\"restricted setting\" because Puraa was installed from external " +
                "sources. Open app settings, tap the ⋮ menu, choose \"Allow " +
                "restricted settings,\" then turn on SMS.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = "Open app settings",
            onClick = onOpenSettings,
            modifier = Modifier,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * The running-status banner: a teal dot with an "Active" headline and the
 * live destination. Shown at the top of the relay screen so the app's state
 * reads at a glance — the header stays just the wordmark.
 */
@Composable
private fun RelayStatusCard(destination: String) {
    PuraaCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(active = true)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Active", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Forwarding to $destination",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    deviceName: String,
    whitelistSummary: String,
    smsGranted: Boolean,
) {
    PuraaCard(modifier = Modifier.fillMaxWidth()) {
        InfoRow("Device", deviceName)
        Spacer(Modifier.height(8.dp))
        InfoRow("Filter", whitelistSummary)
        Spacer(Modifier.height(8.dp))
        InfoRow(
            label = "SMS",
            value = if (smsGranted) "Access granted"
            else "Access revoked",
            valueColor = if (smsGranted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun OutboxRow(row: OutboxEntity) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(row.receivedAt))
    val statusColor = when (row.status) {
        OutboxEntity.Status.SENT -> MaterialTheme.colorScheme.primary
        OutboxEntity.Status.FAILED -> MaterialTheme.colorScheme.error
        OutboxEntity.Status.BACKOFF, OutboxEntity.Status.PENDING ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.sender, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = time + (row.lastErrorMessage?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            row.status.name,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
        )
    }
}


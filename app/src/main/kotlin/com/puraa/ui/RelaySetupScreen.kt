package com.puraa.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.os.Build
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.puraa.R
import com.puraa.config.ConfigStore
import com.puraa.config.Destination
import com.puraa.config.SetupCode
import com.puraa.relay.RelayAnnouncer

/**
 * Defaults that pre-populate the setup screen.
 *
 * Source priority (highest wins):
 *   1. Whatever is already persisted in ConfigStore
 *   2. A device-derived default for [deviceName]
 *
 * Scanning a setup QR overrides the live fields after the screen is shown.
 */
data class RelaySetupPrefill(
    val destination: Destination,
    val token: String,
    val channelId: String,
    val discordWebhook: String,
    val deviceName: String,
    val whitelist: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySetupScreen(
    config: ConfigStore,
    prefill: RelaySetupPrefill,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current

    var destination by remember { mutableStateOf(prefill.destination) }
    var deviceName by remember { mutableStateOf(prefill.deviceName) }
    var token by remember { mutableStateOf(prefill.token) }
    var channelIdText by remember { mutableStateOf(prefill.channelId) }
    var webhook by remember { mutableStateOf(prefill.discordWebhook) }
    var whitelist by remember { mutableStateOf(prefill.whitelist) }
    var error by remember { mutableStateOf<String?>(null) }

    val save: () -> Unit = {
        when (destination) {
            Destination.TELEGRAM ->
                channelIdText.trim().toLongOrNull()?.let { config.setTelegram(token.trim(), it) }
            Destination.DISCORD -> config.setDiscord(webhook.trim())
        }
        config.relayDeviceName = deviceName.trim()
        config.relaySenderWhitelist = whitelist.trim()
        RelayAnnouncer.announceConfigured(context, config)
        onSaved()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // Save regardless — the config is valid either way. If SMS access
        // was denied the relay simply won't catch anything until it's
        // granted from system settings; the status screen flags that.
        if (grants[Manifest.permission.RECEIVE_SMS] == true) {
            requestBatteryExemption(context)
        }
        save()
    }

    // Scan a setup QR from another phone → pre-fill the fields for review.
    // Only an in-person camera scan applies it, so there's no remote-hijack
    // path (unlike an unconfirmed link).
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val setup = result.contents?.let { SetupCode.decode(it) }
        if (setup != null && setup.isComplete()) {
            setup.destination?.let { destination = it }
            setup.token?.let { token = it }
            setup.channelId?.let { channelIdText = it }
            setup.discordWebhook?.let { webhook = it }
            setup.whitelist?.let { whitelist = it }
            setup.user?.takeIf { it.isNotBlank() }?.let { deviceName = "${it.trim()}'s ${Build.MODEL}" }
            error = null
        } else if (result.contents != null) {
            error = "That QR code isn't a valid Puraa setup code."
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = "Puraa",
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        StatusPill(active = false)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            SecondaryButton(
                text = "Scan setup QR code",
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan the Puraa setup QR")
                            setBeepEnabled(false)
                            setOrientationLocked(false)
                        },
                    )
                },
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device name") },
                placeholder = { Text("e.g. Mom's Pixel") },
                singleLine = true,
                supportingText = {
                    Text("Shown on every forwarded SMS so you know which phone it came from.")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Text("Forward to", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Destination.entries.forEachIndexed { index, dest ->
                    SegmentedButton(
                        selected = destination == dest,
                        onClick = { destination = dest; error = null },
                        shape = SegmentedButtonDefaults.itemShape(index, Destination.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(if (dest == Destination.TELEGRAM) "Telegram" else "Discord")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (destination) {
                Destination.TELEGRAM -> {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; error = null },
                        label = { Text("Sender bot token") },
                        placeholder = { Text("1234567890:AAH...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = channelIdText,
                        onValueChange = { channelIdText = it; error = null },
                        label = { Text("Channel id") },
                        placeholder = { Text("-1001234567890") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Destination.DISCORD -> {
                    OutlinedTextField(
                        value = webhook,
                        onValueChange = { webhook = it; error = null },
                        label = { Text("Discord webhook URL") },
                        placeholder = { Text("https://discord.com/api/webhooks/...") },
                        singleLine = true,
                        supportingText = {
                            Text("Channel → Edit → Integrations → Webhooks → Copy Webhook URL.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = whitelist,
                onValueChange = { whitelist = it },
                label = { Text("Sender whitelist (empty = all SMS)") },
                placeholder = { Text("HDFCBK,ICICIB,CRED") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(28.dp))

            PrimaryButton(
                text = "Save and start relay",
                onClick = {
                    error = validate(destination, deviceName, token, channelIdText, webhook)
                    if (error == null) {
                        if (missingRelayPermissions(context).isNotEmpty()) {
                            permissionLauncher.launch(relayRuntimePermissions())
                        } else {
                            requestBatteryExemption(context)
                            save()
                        }
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Puraa reads incoming SMS directly as they arrive and " +
                    "forwards the ones matching your filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Returns an error message for the current inputs, or null if valid. */
private fun validate(
    destination: Destination,
    deviceName: String,
    token: String,
    channelId: String,
    webhook: String,
): String? = when {
    deviceName.isBlank() -> "Device name is required."
    destination == Destination.TELEGRAM && token.trim().isEmpty() -> "Bot token is required."
    destination == Destination.TELEGRAM && channelId.trim().toLongOrNull() == null ->
        "Channel id must be a number (negative for channels)."
    destination == Destination.DISCORD && webhook.trim().isEmpty() -> "Discord webhook URL is required."
    destination == Destination.DISCORD && !isDiscordWebhook(webhook.trim()) ->
        "Enter a real Discord webhook URL (https://discord.com/api/webhooks/…)."
    else -> null
}

/** Only accept a genuine Discord webhook host, so config can't point elsewhere. */
private fun isDiscordWebhook(url: String): Boolean =
    url.startsWith("https://discord.com/api/webhooks/") ||
        url.startsWith("https://discordapp.com/api/webhooks/")

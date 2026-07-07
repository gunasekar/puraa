package com.puraa

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.puraa.config.ConfigStore
import com.puraa.config.Destination
import com.puraa.ui.RelayScreen
import com.puraa.ui.RelaySetupPrefill
import com.puraa.ui.RelaySetupScreen
import com.puraa.ui.theme.PuraaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val configStore = ConfigStore(applicationContext)

        setContent {
            PuraaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PuraaRouter(configStore)
                }
            }
        }
    }
}

@Composable
private fun PuraaRouter(config: ConfigStore) {
    // The relay is configured only two ways, both requiring physical presence:
    // typing the values, or scanning a setup QR in person. Config is written
    // solely by an explicit Save on the setup screen — there is no deep link
    // or any other remote path that could point the relay somewhere.
    var running by remember { mutableStateOf(config.isRelayRunning()) }

    if (running) {
        RelayScreen(
            config = config,
            onStopped = { running = false },
        )
    } else {
        RelaySetupScreen(
            config = config,
            prefill = buildRelayPrefill(config),
            onSaved = { running = true },
        )
    }
}

internal fun buildRelayPrefill(config: ConfigStore): RelaySetupPrefill =
    RelaySetupPrefill(
        destination = config.relayDestination ?: Destination.TELEGRAM,
        token = config.relaySenderBotToken.orEmpty(),
        channelId = config.relayChannelId?.toString().orEmpty(),
        discordWebhook = config.relayDiscordWebhookUrl.orEmpty(),
        deviceName = config.relayDeviceName.ifBlank { defaultDeviceName() },
        whitelist = config.relaySenderWhitelist,
    )

private fun defaultDeviceName(): String =
    listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replaceFirstChar { it.uppercase() }
        .ifBlank { "Android phone" }

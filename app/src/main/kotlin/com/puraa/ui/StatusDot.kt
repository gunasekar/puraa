package com.puraa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A wordless status indicator — a tinted dot in a soft halo. Colour alone
 * carries the state, so it stays compact beside the "Active" label in the
 * status card. The state is exposed to screen readers via a content description.
 *
 * @param active `true` shows the primary-tinted dot (relay running);
 *   `false` a muted dot (stopped / not yet configured).
 */
@Composable
fun StatusDot(active: Boolean) {
    val accent =
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (active) "Relay active" else "Relay inactive"

    Box(
        modifier = Modifier
            .semantics { contentDescription = label }
            .size(20.dp)
            .background(accent.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(accent, CircleShape),
        )
    }
}

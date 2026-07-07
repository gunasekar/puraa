package com.puraa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A small status pill — a tinted, pill-shaped chip with a dot and a one-word
 * state. Colour carries the meaning, so there's no connective label text.
 *
 * @param active `true` shows a primary-tinted "Active" (relay running);
 *   `false` a muted "Inactive" (stopped / not yet configured).
 */
@Composable
fun StatusPill(active: Boolean) {
    val accent =
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .background(accent.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(accent, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (active) "Active" else "Inactive",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
    }
}

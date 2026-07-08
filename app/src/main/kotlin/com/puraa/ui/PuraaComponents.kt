package com.puraa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puraa.R
import com.puraa.ui.theme.Bricolage
import com.puraa.ui.theme.PlexMono

/**
 * Puraa's shared UI vocabulary.
 *
 * Every card and button in the app is built from the primitives here, so
 * corner radius, elevation, borders, padding and colour roles stay identical
 * across screens. Restyle the app by editing this file — never re-declare a
 * shape or border at a call site.
 */

/**
 * The Puraa lockup: dove mark + "Puraa" wordmark with the teal accent dot,
 * matching the website. Use it in top bars instead of the bare logo. The mark
 * is decorative here — the text carries the accessible name.
 */
@Composable
fun PuraaWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 28.dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(logoSize),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildAnnotatedString {
                append("Puraa")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(".") }
            },
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Bricolage),
        )
    }
}

/**
 * A small mono, uppercase, wide-tracked label — the website's "eyebrow". Sits
 * above a section as a quiet, technical caption. Defaults to the accent colour.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text.uppercase(),
        color = color,
        style = TextStyle(
            fontFamily = PlexMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        ),
        modifier = modifier,
    )
}

/** The surface role a [PuraaCard] paints itself with. */
enum class CardTone { Neutral, Accent, Alert }

/**
 * The one card style: theme `large` radius, flat (no elevation), a [tone]-driven
 * colour pair, and a consistent inner padding. Content is laid out in a [Column].
 */
@Composable
fun PuraaCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Neutral,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = when (tone) {
        CardTone.Neutral -> MaterialTheme.colorScheme.surface
        CardTone.Accent -> MaterialTheme.colorScheme.primaryContainer
        CardTone.Alert -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (tone) {
        CardTone.Neutral -> MaterialTheme.colorScheme.onSurface
        CardTone.Accent -> MaterialTheme.colorScheme.onPrimaryContainer
        CardTone.Alert -> MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * The single filled call-to-action — one per screen (e.g. "Save"). Full width
 * by default. Pass a [leadingIcon] to prefix an icon before the label.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        ButtonContent(text, leadingIcon)
    }
}

/**
 * Medium-high emphasis: a soft-teal filled action that sits between the single
 * [PrimaryButton] and the outlined [SecondaryButton]. For a recurring, real
 * action that deserves more weight than a plain secondary (e.g. "Push last 15
 * minutes") without competing with the screen's one primary CTA. Uses the
 * tonal accent tier so it stays in the accent family, not M3's default grey.
 */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/**
 * Every non-primary action: outlined with a consistent 1.5dp border in
 * [contentColor] (defaults to the accent; pass e.g. `onErrorContainer` when the
 * button sits inside an [CardTone.Alert] card). Full width by default; pass a
 * plain [Modifier] to let it wrap.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.5.dp, contentColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: (@Composable () -> Unit)?) {
    if (leadingIcon != null) {
        leadingIcon()
        Spacer(Modifier.width(8.dp))
    }
    Text(text)
}

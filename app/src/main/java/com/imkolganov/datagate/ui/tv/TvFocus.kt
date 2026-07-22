package com.imkolganov.datagate.ui.tv

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visible focus ring for D-pad / keyboard. On phone (non-TV) stays a no-op so UI is unchanged.
 * Observes focus via [onFocusChanged] so it works on Material buttons that already own focus.
 */
fun Modifier.tvFocusBorder(
    shape: Shape = RectangleShape,
    width: Dp = 3.dp,
    color: Color? = null,
): Modifier = composed {
    if (!LocalIsTelevision.current) return@composed this

    var focused by remember { mutableStateOf(false) }
    val ringColor = color ?: MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(
            if (focused) {
                Modifier.border(width = width, color = ringColor, shape = shape)
            } else {
                Modifier
            }
        )
}

/**
 * Focusable clickable with TV focus ring. On phone keeps default Material ripple indication.
 */
fun Modifier.tvClickable(
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    onClick: () -> Unit,
): Modifier = composed {
    val isTv = LocalIsTelevision.current
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val ringColor = MaterialTheme.colorScheme.primary
    val indication: Indication? = if (isTv) null else LocalIndication.current

    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(
            if (isTv && focused) {
                Modifier.border(width = 3.dp, color = ringColor, shape = shape)
            } else {
                Modifier
            }
        )
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = indication,
            onClick = onClick,
        )
}

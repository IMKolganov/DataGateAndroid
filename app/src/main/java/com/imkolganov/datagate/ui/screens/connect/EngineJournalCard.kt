package com.imkolganov.datagate.ui.screens.connect

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.method.ScrollingMovementMethod
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.imkolganov.datagate.R
import com.imkolganov.datagate.logger.EngineJournal

@Composable
fun EngineJournalCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val canAct = EngineJournalActionsPolicy.hasCopyableContent(text)
    val copiedMessage = stringResource(R.string.copied)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val waitingColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val waitingText = stringResource(R.string.home_engine_logs_waiting)
    var stickToBottom by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .padding(horizontal = 8.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_engine_logs),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        if (copyEngineJournalToClipboard(context, text)) {
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = canAct,
                ) {
                    Text(stringResource(R.string.home_engine_logs_copy))
                }
                TextButton(
                    onClick = { EngineJournal.clear() },
                    enabled = canAct,
                ) {
                    Text(stringResource(R.string.home_engine_logs_clear))
                }
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                factory = { ctx ->
                    @SuppressLint("ClickableViewAccessibility")
                    TextView(ctx).apply {
                        typeface = Typeface.MONOSPACE
                        textSize = 11f
                        setLineSpacing(0f, 1.15f)
                        setTextIsSelectable(true)
                        movementMethod = ScrollingMovementMethod.getInstance()
                        setHorizontallyScrolling(false)
                        setOnTouchListener { touched, event ->
                            touched.parent?.requestDisallowInterceptTouchEvent(true)
                            if (event.actionMasked == MotionEvent.ACTION_UP ||
                                event.actionMasked == MotionEvent.ACTION_CANCEL
                            ) {
                                touched.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                val tv = touched as TextView
                                val layout = tv.getLayout()
                                val maxScroll = if (layout == null) {
                                    0
                                } else {
                                    (layout.getLineTop(tv.lineCount) - tv.height).coerceAtLeast(0)
                                }
                                stickToBottom = EngineJournalScrollPolicy.shouldStickToBottom(
                                    scrollValue = tv.scrollY,
                                    maxValue = maxScroll,
                                )
                            }
                            false
                        }
                    }
                },
                update = { view ->
                    val display = text.ifBlank { waitingText }
                    val color = if (text.isBlank()) waitingColor else textColor
                    if (view.text.toString() != display) {
                        view.setTextColor(color)
                        view.text = display
                    } else if (view.currentTextColor != color) {
                        view.setTextColor(color)
                    }
                    if (stickToBottom) {
                        view.post {
                            val layout = view.getLayout() ?: return@post
                            val maxScroll = (layout.getLineTop(view.lineCount) - view.height).coerceAtLeast(0)
                            view.scrollTo(0, maxScroll)
                        }
                    }
                },
            )
        }
    }
}

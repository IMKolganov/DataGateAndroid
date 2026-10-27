package com.imkolganov.datagate.ui.screens.connect

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.logger.EngineJournal
import com.imkolganov.datagate.vpn.VpnReconnectCountPolicy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun EngineJournalCard(
    text: String,
    modifier: Modifier = Modifier,
    reconnectCount: Int = 0,
    sessionActive: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canAct = EngineJournalActionsPolicy.hasCopyableContent(text)
    val copiedMessage = stringResource(R.string.copied)
    val waitingText = stringResource(R.string.home_engine_logs_waiting)
    val bottomLabel = stringResource(R.string.home_engine_logs_bottom)
    val sessionCount = VpnReconnectCountPolicy.estimatedSessions(reconnectCount, sessionActive)
    val reconnectsLabel = stringResource(
        R.string.home_engine_logs_reconnects,
        reconnectCount,
        sessionCount,
    )
    val display = text.ifBlank { waitingText }
    val scrollState = rememberScrollState()
    var stickToBottom by remember { mutableStateOf(true) }
    val canScrollDown = canAct && scrollState.maxValue > 0 && !stickToBottom

    fun jumpToBottom() {
        stickToBottom = true
        scope.launch {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow {
            EngineJournalScrollPolicy.shouldStickToBottom(
                scrollValue = scrollState.value,
                maxValue = scrollState.maxValue,
            )
        }
            .distinctUntilChanged()
            .collect { stickToBottom = it }
    }

    LaunchedEffect(display, stickToBottom) {
        if (stickToBottom) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

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
                    onClick = { jumpToBottom() },
                    enabled = canScrollDown,
                ) {
                    Text(bottomLabel)
                }
                TextButton(
                    onClick = { EngineJournal.clear() },
                    enabled = canAct,
                ) {
                    Text(stringResource(R.string.home_engine_logs_clear))
                }
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = display,
                    color = if (text.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(JOURNAL_VIEWPORT_HEIGHT)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                        .verticalScroll(scrollState)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                if (canScrollDown) {
                    FilledTonalIconButton(
                        onClick = { jumpToBottom() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = bottomLabel,
                        )
                    }
                }
            }
            Text(
                text = reconnectsLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

private val JOURNAL_VIEWPORT_HEIGHT = 260.dp

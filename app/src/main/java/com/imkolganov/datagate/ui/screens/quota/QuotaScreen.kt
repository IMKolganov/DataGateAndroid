package com.imkolganov.datagate.ui.screens.quota

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.screens.access.AccessContract
import com.imkolganov.datagate.ui.tv.LocalIsTelevision
import com.imkolganov.datagate.ui.tv.tvFocusBorder
import com.imkolganov.datagate.util.formatBytes
import com.imkolganov.datagate.util.formatQuotaEffectiveFromForDisplay
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun QuotaScreen(
    state: AccessContract.UiState,
    onEvent: (AccessContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    primaryFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val isTelevision = LocalIsTelevision.current
    val quotaRefreshing = state.isQuotaLoading || state.isTrafficLoading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = quotaRefreshing,
        onRefresh = { onEvent(AccessContract.UiEvent.RefreshQuota) }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isTelevision) Modifier
                else Modifier.pullRefresh(pullRefreshState)
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(if (isTelevision) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTelevision) 16.dp else 12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.nav_quota),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = { onEvent(AccessContract.UiEvent.RefreshQuota) },
                        modifier = Modifier
                            .then(
                                if (primaryFocusRequester != null) {
                                    Modifier.focusRequester(primaryFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .tvFocusBorder(shape = RoundedCornerShape(50)),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.access_refresh)
                        )
                    }
                }
            }

            item {
                QuotaPlansContent(
                    quota = state.quota,
                    isQuotaLoading = state.isQuotaLoading,
                    isTrafficLoading = state.isTrafficLoading,
                )
            }

            item {
                Box(modifier = Modifier.padding(bottom = 24.dp))
            }
        }

        if (!isTelevision) {
            PullRefreshIndicator(
                refreshing = quotaRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun QuotaPlansContent(
    quota: AccessContract.QuotaUiState,
    isQuotaLoading: Boolean,
    isTrafficLoading: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.access_quota_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isQuotaLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        quota.errorText?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            elevation = AppCards.defaultElevation(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.access_quota_current_plan),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                when {
                    isQuotaLoading && quota.currentPlanName == null && quota.errorText == null -> {
                        Text(
                            text = stringResource(R.string.access_quota_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    quota.currentPlanName != null -> {
                        Text(
                            text = quota.currentPlanName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        quota.currentEffectiveFrom?.takeIf { it.isNotBlank() }?.let { from ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.access_quota_effective_from,
                                    formatQuotaEffectiveFromForDisplay(from)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        quota.currentNote?.takeIf { it.isNotBlank() }?.let { note ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.access_quota_no_active),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (quota.errorText == null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.access_quota_traffic_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                isQuotaLoading || isTrafficLoading -> {
                    Text(
                        text = stringResource(R.string.access_quota_traffic_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                quota.trafficUsageNeedsExternalId -> {
                    Text(
                        text = stringResource(R.string.access_quota_needs_external_id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                quota.quotaLimitBytes <= 0L -> {
                    Text(
                        text = stringResource(R.string.access_quota_no_traffic_cap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                quota.trafficUsedBytesForPeriod < 0L -> {
                    Text(
                        text = stringResource(R.string.access_quota_usage_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val used = quota.trafficUsedBytesForPeriod
                    val lim = quota.quotaLimitBytes
                    val pct = if (lim > 0) 100.0 * used.toDouble() / lim.toDouble() else 0.0
                    val over = used > lim && lim > 0
                    val barFraction =
                        if (lim > 0) (used.toDouble() / lim.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f
                    val periodLabel = if (quota.quotaPeriodIsMonthly) {
                        stringResource(R.string.access_quota_period_month)
                    } else {
                        stringResource(R.string.access_quota_period_today)
                    }
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { barFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = if (over) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.access_quota_used_line,
                            formatBytes(used),
                            formatBytes(lim),
                            String.format(Locale.US, "%.1f", pct)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (over) {
                            stringResource(
                                R.string.access_quota_over_by,
                                formatBytes(used - lim)
                            )
                        } else {
                            stringResource(
                                R.string.access_quota_remaining,
                                formatBytes(lim - used)
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        if (quota.allPlans.isNotEmpty()) {
            Text(
                text = stringResource(R.string.access_quota_all_plans_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            quota.allPlans.forEach { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCards.shape,
                    elevation = AppCards.defaultElevation()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plan.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (plan.isDefault) {
                                    Text(
                                        text = stringResource(R.string.access_quota_plan_default),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (!plan.isActive) {
                                    Text(
                                        text = stringResource(R.string.access_quota_plan_inactive),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        plan.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

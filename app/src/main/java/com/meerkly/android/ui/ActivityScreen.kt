package com.meerkly.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meerkly.android.R
import com.meerkly.android.ui.components.ContentColumn
import com.meerkly.android.ui.components.EmptyState
import com.meerkly.android.ui.components.StatusChip
import com.meerkly.android.ui.components.TwoPane
import com.meerkly.android.ui.nav.NavState
import com.meerkly.android.ui.nav.WindowWidth
import com.meerkly.android.ui.theme.Display
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Emerald
import com.meerkly.android.ui.theme.EmeraldDeep
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Rose
import com.meerkly.android.ui.theme.RoseDeep
import com.meerkly.android.ui.theme.Sand
import com.meerkly.android.util.Formatters
import com.meerkly.android.util.RelativeTime
import java.time.Instant

/**
 * What this device has actually fetched — the app's proof of work.
 *
 * The feed is the in-memory ring, so it is honestly labelled "since the app
 * started" rather than pretending to be history: RecentNavigationRepository
 * deliberately starts empty each launch, and its JSON snapshot is documented
 * as diagnostics-only.
 */
@Composable
fun ActivityScreen(viewModel: MainViewModel, nav: NavState, width: WindowWidth) {
    val recent by viewModel.recent.collectAsState()
    val workerEnabled by viewModel.workerEnabled.collectAsState()
    ActivityContent(
        rows = ActivityFeed.rows(recent),
        summary = ActivityFeed.summary(recent),
        workerEnabled = workerEnabled,
        twoPane = width.twoPane(nav.destination),
        selectedKey = nav.activityKey,
        onSelect = { nav.activityKey = it },
    )
}

/**
 * Plain state in, lambdas out — no ViewModel. That's what lets this render in a
 * JVM test (instantiating MainViewModel would drag in AppGraph and a
 * GeckoRuntime) and it keeps the screen honest about what it actually needs.
 */
@Composable
fun ActivityContent(
    rows: List<ActivityFeed.Row>,
    summary: ActivityFeed.Summary,
    workerEnabled: Boolean,
    twoPane: Boolean,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.activity_empty_title),
            note = stringResource(
                if (workerEnabled) R.string.activity_empty_note
                else R.string.activity_empty_stopped_note,
            ),
        )
        return
    }

    if (twoPane) {
        TwoPane(
            list = { FeedList(rows, summary, selectedKey, onSelect) },
            detail = {
                val selected = rows.firstOrNull { it.key == selectedKey }
                if (selected == null) {
                    EmptyState(
                        title = stringResource(R.string.activity_select_title),
                        note = stringResource(R.string.activity_select_hint),
                    )
                } else {
                    FeedDetail(selected)
                }
            },
        )
        return
    }

    // Compact/medium: the detail replaces the list.
    val selected = rows.firstOrNull { it.key == selectedKey }
    if (selected != null) FeedDetail(selected)
    else FeedList(rows, summary, null, onSelect)
}

@Composable
private fun FeedList(
    rows: List<ActivityFeed.Row>,
    summary: ActivityFeed.Summary,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ContentColumn(verticalSpacing = 10.dp) {
                Text(
                    text = stringResource(R.string.nav_activity),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                )
                SummaryStrip(summary)
                Text(
                    text = stringResource(R.string.activity_since_start),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                )
            }
        }
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            // Hairlines rather than gaps: the rows read as one continuous log,
            // which is what a feed should feel like, and it stops five entries
            // from looking like five floating cards.
            if (index > 0) HorizontalDivider(color = Sand.copy(alpha = 0.6f), thickness = 1.dp)
            FeedRow(row, selected = row.key == selectedKey) { onSelect(row.key) }
        }
    }
}

@Composable
private fun SummaryStrip(summary: ActivityFeed.Summary) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(summary.pages.toString(), stringResource(R.string.activity_stat_pages), Modifier.weight(1f))
        Stat(
            summary.successPercent?.let { "$it%" } ?: Formatters.UNKNOWN_VALUE,
            stringResource(R.string.activity_stat_ok),
            Modifier.weight(1f),
        )
        Stat(
            summary.medianMs?.let { Formatters.duration(it) } ?: Formatters.UNKNOWN_VALUE,
            stringResource(R.string.activity_stat_speed),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Cream,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Sand),
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 10.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                maxLines = 1,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = InkSoft, maxLines = 1)
        }
    }
}

@Composable
private fun FeedRow(row: ActivityFeed.Row, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) Sand.copy(alpha = 0.45f) else Cream) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (row.succeeded) Emerald else Rose, CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    row.host,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.title ?: row.path.ifBlank { row.requestedUrl },
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                row.loadedMs?.let { Formatters.duration(it) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun FeedDetail(row: ActivityFeed.Row) {
    ContentColumn(modifier = Modifier.fillMaxSize()) {
        StatusChip(
            text = stringResource(
                if (row.succeeded) R.string.activity_ok else R.string.activity_failed,
            ),
            fg = if (row.succeeded) EmeraldDeep else RoseDeep,
            bg = (if (row.succeeded) Emerald else Rose).copy(alpha = 0.12f),
        )
        Text(
            row.host,
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
        )
        row.title?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        }
        DetailRow(stringResource(R.string.activity_detail_requested), row.requestedUrl)
        row.finalUrl?.takeIf { it != row.requestedUrl }?.let {
            DetailRow(stringResource(R.string.activity_detail_final), it)
        }
        DetailRow(
            stringResource(R.string.activity_detail_duration),
            row.loadedMs?.let { Formatters.duration(it) } ?: Formatters.UNKNOWN_VALUE,
        )
        row.sizeBytes?.let {
            DetailRow(stringResource(R.string.activity_detail_size), Formatters.bytes(it))
        }
        DetailRow(stringResource(R.string.activity_detail_started), relative(row.startedAt))
        row.error?.let {
            DetailRow(stringResource(R.string.activity_detail_error), it)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = InkSoft,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

/** Bucket → copy. Kept at the call site so the util stays free of English. */
@Composable
private fun relative(then: Instant): String =
    when (val b = RelativeTime.bucket(then, Instant.now())) {
        RelativeTime.Bucket.JustNow -> stringResource(R.string.time_just_now)
        is RelativeTime.Bucket.Minutes -> stringResource(R.string.time_minutes, b.value)
        is RelativeTime.Bucket.Hours -> stringResource(R.string.time_hours, b.value)
        RelativeTime.Bucket.Yesterday -> stringResource(R.string.time_yesterday)
        is RelativeTime.Bucket.Days -> stringResource(R.string.time_days, b.value)
        RelativeTime.Bucket.Never -> stringResource(R.string.time_never)
    }

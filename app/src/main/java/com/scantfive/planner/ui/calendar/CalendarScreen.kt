package com.scantfive.planner.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantfive.planner.R
import com.scantfive.planner.ui.formatDateTime
import com.scantfive.planner.ui.formatDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Сколько «дорожек» протяжённых событий показывать в неделе; остальные видны в списке дня. */
private const val MAX_LANES = 3
private const val MAX_DOTS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onAddTask: (LocalDate) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weeks = remember(month, firstDayOfWeek) { monthWeeks(month, firstDayOfWeek) }
    val dayEvents = remember(events, selected) { events.filter { it.covers(selected) } }
    val today = LocalDate.now()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_calendar)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddTask(selected) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task))
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            item {
                MonthHeader(
                    month = month,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    onToday = viewModel::goToToday,
                )
            }
            item { WeekdayHeader(firstDayOfWeek) }
            items(weeks) { week ->
                WeekRow(
                    week = week,
                    month = month,
                    today = today,
                    selected = selected,
                    events = events,
                    onSelect = viewModel::select,
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    formatDay(selected.toEpochDay()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (dayEvents.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_events_day),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(dayEvents, key = { it.taskId }) { event ->
                    EventRow(event, onClick = { onOpenTask(event.taskId) })
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        .replaceFirstChar { it.uppercase() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.KeyboardArrowLeft, stringResource(R.string.prev_month))
        }
        Text(
            "$name ${month.year}",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.KeyboardArrowRight, stringResource(R.string.next_month))
        }
        TextButton(onClick = onToday) { Text(stringResource(R.string.today)) }
    }
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: java.time.DayOfWeek) {
    Row(Modifier.fillMaxWidth()) {
        (0L..6L).forEach { shift ->
            Text(
                firstDayOfWeek.plus(shift).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekRow(
    week: List<LocalDate>,
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    events: List<CalendarEvent>,
    onSelect: (LocalDate) -> Unit,
) {
    val segments = remember(week, events) { weekSegments(week, events).filter { it.lane < MAX_LANES } }
    val laneCount = (segments.maxOfOrNull { it.lane } ?: -1) + 1

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                DayNumber(
                    day = day,
                    inMonth = YearMonth.from(day) == month,
                    isToday = day == today,
                    isSelected = day == selected,
                    onClick = { onSelect(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(laneCount) { lane ->
            LaneRow(segments.filter { it.lane == lane }.sortedBy { it.startCol }, week, onSelect)
        }
        Row(Modifier.fillMaxWidth().height(10.dp)) {
            week.forEach { day ->
                DotsCell(dotsFor(day, events), Modifier.weight(1f).clickable { onSelect(day) })
            }
        }
    }
}

@Composable
private fun DayNumber(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.height(32.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(28.dp).background(if (isSelected) colors.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else null,
                color = when {
                    isSelected -> colors.onPrimary
                    isToday -> colors.primary
                    inMonth -> colors.onSurface
                    else -> colors.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

/** Одна «дорожка» протяжённых событий: полосы со скруглением только на настоящих началах и концах. */
@Composable
private fun LaneRow(segments: List<BarSegment>, week: List<LocalDate>, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        var column = 0
        segments.forEach { segment ->
            if (segment.startCol > column) {
                Spacer(Modifier.weight((segment.startCol - column).toFloat()))
            }
            val radius = 7.dp
            val shape = RoundedCornerShape(
                topStart = if (segment.roundStart) radius else 0.dp,
                bottomStart = if (segment.roundStart) radius else 0.dp,
                topEnd = if (segment.roundEnd) radius else 0.dp,
                bottomEnd = if (segment.roundEnd) radius else 0.dp,
            )
            val color = Color(segment.event.color).copy(alpha = if (segment.event.isDone) 0.4f else 1f)
            Box(
                Modifier
                    .weight((segment.endCol - segment.startCol + 1).toFloat())
                    .padding(start = if (segment.roundStart) 2.dp else 0.dp, end = if (segment.roundEnd) 2.dp else 0.dp)
                    .height(14.dp)
                    .background(color, shape)
                    .clickable { onSelect(week[segment.startCol]) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    segment.event.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            column = segment.endCol + 1
        }
    }
}

@Composable
private fun DotsCell(dayEvents: List<CalendarEvent>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        dayEvents.take(MAX_DOTS).forEach { event ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(6.dp)
                    .background(Color(event.color).copy(alpha = if (event.isDone) 0.4f else 1f), CircleShape),
            )
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(6.dp)
                    .height(36.dp)
                    .background(Color(event.color), RoundedCornerShape(3.dp)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (event.isDone) TextDecoration.LineThrough else null,
                )
                val range = if (event.isMultiDay) {
                    "${formatDateTime(event.dueAt)} – ${formatDay(event.end.toEpochDay())}"
                } else {
                    formatDateTime(event.dueAt)
                }
                Text(
                    range,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

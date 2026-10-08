package com.scantfive.planner.ui.calendar

import com.scantfive.planner.data.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Событие календаря: задача со сроком, занимающая дни от [start] до [end] включительно. */
data class CalendarEvent(
    val taskId: Long,
    val title: String,
    val color: Int,
    val dueAt: Long,
    val start: LocalDate,
    val end: LocalDate,
    val isDone: Boolean,
) {
    val isMultiDay: Boolean get() = end.isAfter(start)

    fun covers(day: LocalDate): Boolean = !day.isBefore(start) && !day.isAfter(end)
}

fun epochDayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

/** Сколько «дорожек» протяжённых событий рисовать в неделе; остальные видны в списке дня. */
const val MAX_LANES = 3

/** Сколько точек однодневных событий помещается в клетку дня. */
const val MAX_DOTS = 5

/** «Октябрь 2026». */
fun monthTitle(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } +
        " " + month.year

/** Задача без срока в календаре не показывается. */
fun Task.toCalendarEvent(zone: ZoneId = ZoneId.systemDefault()): CalendarEvent? {
    val due = dueAt ?: return null
    val start = LocalDate.ofEpochDay(epochDayOf(due, zone))
    val end = endDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { it.isAfter(start) } ?: start
    return CalendarEvent(id, title, color, due, start, end, isDone)
}

/** Недели, покрывающие месяц целиком (включая дни соседних месяцев). */
fun monthWeeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate>> {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    var weekStart = first.minusDays(offset.toLong())
    val weeks = mutableListOf<List<LocalDate>>()
    while (!weekStart.isAfter(month.atEndOfMonth())) {
        weeks += (0L..6L).map { weekStart.plusDays(it) }
        weekStart = weekStart.plusDays(7)
    }
    return weeks
}

/** Полоса протяжённого события в пределах одной недели. Колонки 0..6. */
data class BarSegment(
    val event: CalendarEvent,
    val startCol: Int,
    val endCol: Int,
    val lane: Int,
    /** Скруглить левый край (событие начинается на этой неделе). */
    val roundStart: Boolean,
    /** Скруглить правый край (событие заканчивается на этой неделе). */
    val roundEnd: Boolean,
)

/** Раскладывает протяжённые события недели по «дорожкам» так, чтобы полосы не пересекались. */
fun weekSegments(week: List<LocalDate>, events: List<CalendarEvent>): List<BarSegment> {
    val first = week.first()
    val last = week.last()
    val multiDay = events
        .filter { it.isMultiDay && !it.end.isBefore(first) && !it.start.isAfter(last) }
        .sortedWith(
            compareBy<CalendarEvent> { it.start }
                .thenByDescending { ChronoUnit.DAYS.between(it.start, it.end) }
                .thenBy { it.taskId },
        )
    val laneEnds = mutableListOf<Int>()
    return multiDay.map { event ->
        val startCol = if (event.start.isBefore(first)) 0 else ChronoUnit.DAYS.between(first, event.start).toInt()
        val endCol = if (event.end.isAfter(last)) 6 else ChronoUnit.DAYS.between(first, event.end).toInt()
        var lane = laneEnds.indexOfFirst { it < startCol }
        if (lane < 0) {
            lane = laneEnds.size
            laneEnds.add(endCol)
        } else {
            laneEnds[lane] = endCol
        }
        BarSegment(
            event = event,
            startCol = startCol,
            endCol = endCol,
            lane = lane,
            roundStart = !event.start.isBefore(first),
            roundEnd = !event.end.isAfter(last),
        )
    }
}

/** Однодневные события дня — они рисуются точками. */
fun dotsFor(day: LocalDate, events: List<CalendarEvent>): List<CalendarEvent> =
    events.filter { !it.isMultiDay && it.start == day }

/**
 * Срок по умолчанию для новой задачи, созданной с выбранного в календаре дня.
 * Для прошедших дней срока нет; для сегодняшнего — ближайший круглый час.
 */
fun defaultDueMillis(day: LocalDate, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val today = now.toLocalDate()
    val time = when {
        day.isBefore(today) -> return null
        day.isAfter(today) -> LocalTime.of(9, 0)
        else -> {
            val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
            if (nextHour.toLocalDate() == today) nextHour.toLocalTime() else LocalTime.of(23, 59)
        }
    }
    return LocalDateTime.of(day, time).atZone(zone).toInstant().toEpochMilli()
}

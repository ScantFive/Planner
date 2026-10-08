package com.scantfive.planner

import com.scantfive.planner.data.Task
import com.scantfive.planner.ui.calendar.CalendarEvent
import com.scantfive.planner.ui.calendar.defaultDueMillis
import com.scantfive.planner.ui.calendar.dotsFor
import com.scantfive.planner.ui.calendar.monthTitle
import com.scantfive.planner.ui.calendar.monthWeeks
import com.scantfive.planner.ui.calendar.toCalendarEvent
import com.scantfive.planner.ui.calendar.weekSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

class CalendarModelTest {
    private fun event(id: Long, start: LocalDate, end: LocalDate = start) =
        CalendarEvent(id, "e$id", 0, 0L, start, end, isDone = false)

    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    @Test
    fun monthWeeksCoverWholeMonthStartingOnFirstDayOfWeek() {
        val weeks = monthWeeks(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), weeks.last().last())
        assertTrue(weeks.all { it.size == 7 })
    }

    @Test
    fun taskWithoutDueIsNotShown() {
        assertNull(Task(title = "a").toCalendarEvent(ZoneOffset.UTC))
    }

    @Test
    fun endDayNotAfterStartMeansSingleDay() {
        val due = d(8).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val before = Task(title = "a", dueAt = due, endDay = d(7).toEpochDay()).toCalendarEvent(ZoneOffset.UTC)!!
        assertFalse(before.isMultiDay)
        val same = Task(title = "a", dueAt = due, endDay = d(8).toEpochDay()).toCalendarEvent(ZoneOffset.UTC)!!
        assertFalse(same.isMultiDay)
        val span = Task(title = "a", dueAt = due, endDay = d(10).toEpochDay()).toCalendarEvent(ZoneOffset.UTC)!!
        assertTrue(span.isMultiDay)
        assertEquals(d(10), span.end)
    }

    @Test
    fun segmentsAreRoundedOnlyAtRealEnds() {
        val week1 = (5L..11L).map { LocalDate.of(2026, 10, 1).plusDays(it - 1) }
        val week2 = (12L..18L).map { LocalDate.of(2026, 10, 1).plusDays(it - 1) }
        val spill = event(1, d(7), d(13))

        val first = weekSegments(week1, listOf(spill)).single()
        assertEquals(2, first.startCol)
        assertEquals(6, first.endCol)
        assertTrue(first.roundStart)
        assertFalse(first.roundEnd)

        val second = weekSegments(week2, listOf(spill)).single()
        assertEquals(0, second.startCol)
        assertEquals(1, second.endCol)
        assertFalse(second.roundStart)
        assertTrue(second.roundEnd)
    }

    @Test
    fun overlappingEventsGetDifferentLanesAndFreeLanesAreReused() {
        val week = (5L..11L).map { LocalDate.of(2026, 10, 1).plusDays(it - 1) }
        val a = event(1, d(6), d(8))
        val b = event(2, d(7), d(11))
        val c = event(3, d(9), d(10))
        val lanes = weekSegments(week, listOf(c, b, a)).associate { it.event.taskId to it.lane }
        assertEquals(0, lanes[1])
        assertEquals(1, lanes[2])
        assertEquals(0, lanes[3])
    }

    @Test
    fun singleDayEventsAreDotsNotBars() {
        val week = (5L..11L).map { LocalDate.of(2026, 10, 1).plusDays(it - 1) }
        val single = event(1, d(8))
        val multi = event(2, d(7), d(9))
        assertTrue(weekSegments(week, listOf(single)).isEmpty())
        assertEquals(listOf(single), dotsFor(d(8), listOf(single, multi)))
        assertTrue(dotsFor(d(8), listOf(multi)).isEmpty())
    }

    @Test
    fun eventsOutsideWeekAreSkipped() {
        val week = (5L..11L).map { LocalDate.of(2026, 10, 1).plusDays(it - 1) }
        assertTrue(weekSegments(week, listOf(event(1, d(1), d(4)), event(2, d(12), d(14)))).isEmpty())
    }

    @Test
    fun defaultDueForDay() {
        val now = LocalDateTime.of(2026, 10, 8, 10, 20)
        fun at(day: LocalDate, time: LocalTime) = LocalDateTime.of(day, time).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertNull(defaultDueMillis(d(7), now, ZoneOffset.UTC))
        assertEquals(at(d(8), LocalTime.of(11, 0)), defaultDueMillis(d(8), now, ZoneOffset.UTC))
        assertEquals(at(d(20), LocalTime.of(9, 0)), defaultDueMillis(d(20), now, ZoneOffset.UTC))
        val late = LocalDateTime.of(2026, 10, 8, 23, 30)
        assertEquals(at(d(8), LocalTime.of(23, 59)), defaultDueMillis(d(8), late, ZoneOffset.UTC))
    }

    @Test
    fun monthTitleIsCapitalizedStandaloneName() {
        assertEquals("Октябрь 2026", monthTitle(YearMonth.of(2026, 10), Locale.forLanguageTag("ru")))
    }
}

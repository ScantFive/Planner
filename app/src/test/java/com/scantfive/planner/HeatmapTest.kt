package com.scantfive.planner

import com.scantfive.planner.ui.calendar.CalendarEvent
import com.scantfive.planner.ui.calendar.heatAlpha
import com.scantfive.planner.ui.calendar.heatLevel
import com.scantfive.planner.ui.calendar.heatScore
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HeatmapTest {
    private val today = LocalDate.of(2026, 10, 8)
    private var nextId = 1L

    private fun ending(day: LocalDate, start: LocalDate = day, done: Boolean = false) =
        CalendarEvent(nextId++, "e", 0, 0L, start, day, isDone = done)

    @Test
    fun noEventsIsCold() {
        assertEquals(0.0, heatScore(today, emptyList()), 0.0)
        assertEquals(0, heatLevel(today, emptyList()))
    }

    @Test
    fun deadlinesTodayTomorrowAndDayAfterAreWeighted() {
        assertEquals(1.0, heatScore(today, listOf(ending(today))), 1e-9)
        assertEquals(0.5, heatScore(today, listOf(ending(today.plusDays(1)))), 1e-9)
        assertEquals(0.25, heatScore(today, listOf(ending(today.plusDays(2)))), 1e-9)
        assertEquals(0.0, heatScore(today, listOf(ending(today.plusDays(3)))), 1e-9)
        assertEquals(0.0, heatScore(today, listOf(ending(today.minusDays(1)))), 1e-9)
        assertEquals(1, heatLevel(today, listOf(ending(today.plusDays(2)))))
    }

    @Test
    fun scoresAddUp() {
        val events = listOf(ending(today), ending(today), ending(today.plusDays(1)))
        assertEquals(2.5, heatScore(today, events), 1e-9)
        assertEquals(3, heatLevel(today, events))
        assertEquals(4, heatLevel(today, List(4) { ending(today) }))
    }

    @Test
    fun levelBoundariesAreInclusiveAbove() {
        assertEquals(1, heatLevel(today, List(1) { ending(today) }))
        assertEquals(2, heatLevel(today, List(2) { ending(today) }))
        assertEquals(3, heatLevel(today, List(3) { ending(today) }))
        assertEquals(2, heatLevel(today, listOf(ending(today), ending(today.plusDays(1)), ending(today.plusDays(2)))))
    }

    @Test
    fun doneEventsAreIgnored() {
        assertEquals(0, heatLevel(today, listOf(ending(today, done = true))))
    }

    @Test
    fun multiDayEventCountsOnlyItsLastDay() {
        val trip = ending(LocalDate.of(2026, 10, 9), start = LocalDate.of(2026, 10, 6))
        assertEquals(0.5, heatScore(LocalDate.of(2026, 10, 8), listOf(trip)), 1e-9)
        assertEquals(0.0, heatScore(LocalDate.of(2026, 10, 6), listOf(trip)), 1e-9)
    }

    @Test
    fun alphaGrowsTenPercentPerLevel() {
        assertEquals(0f, heatAlpha(0), 1e-6f)
        assertEquals(0.1f, heatAlpha(1), 1e-6f)
        assertEquals(0.4f, heatAlpha(4), 1e-6f)
    }
}

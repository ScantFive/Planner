package com.scantfive.planner.ui.calendar

import java.time.LocalDate

/** Вес дедлайна в зависимости от того, через сколько дней от [day] он наступает: сегодня, завтра, послезавтра. */
private val DEADLINE_WEIGHTS = doubleArrayOf(1.0, 0.5, 0.25)

/** Нагрузка дня: невыполненные события, которые завершаются сегодня, завтра или послезавтра, с весами. */
fun heatScore(day: LocalDate, events: List<CalendarEvent>): Double =
    events.sumOf { event ->
        if (event.isDone) return@sumOf 0.0
        val offset = event.end.toEpochDay() - day.toEpochDay()
        if (offset in DEADLINE_WEIGHTS.indices) DEADLINE_WEIGHTS[offset.toInt()] else 0.0
    }

/** Уровень заливки 0..4 по порогам (0; 1], (1; 2], (2; 3], > 3. */
fun heatLevel(day: LocalDate, events: List<CalendarEvent>): Int {
    val score = heatScore(day, events)
    return when {
        score <= 0.0 -> 0
        score <= 1.0 -> 1
        score <= 2.0 -> 2
        score <= 3.0 -> 3
        else -> 4
    }
}

/** Прозрачность заливки для уровня: 10 % на уровень. */
fun heatAlpha(level: Int): Float = level * 0.10f

package com.scantfive.planner

import com.scantfive.planner.data.TaskColors
import com.scantfive.planner.quickadd.ParsedTask
import com.scantfive.planner.quickadd.QuickAddParser
import com.scantfive.planner.quickadd.toTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class QuickAddParserTest {
    /** Четверг. */
    private val now = LocalDateTime.of(2026, 10, 8, 10, 20)

    private fun parse(text: String) = QuickAddParser.parse(text, now)
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0, year: Int = 2026) =
        LocalDateTime.of(year, month, day, hour, minute)

    @Test
    fun plainTextHasNoDue() {
        assertEquals(ParsedTask("Купить хлеб", null, null, null), parse("Купить хлеб"))
    }

    @Test
    fun dayWordAndTime() {
        val result = parse("Созвон с Аней завтра в 15")
        assertEquals("Созвон с Аней", result.title)
        assertEquals(at(10, 9, 15), result.dueAt)
    }

    @Test
    fun clockTimeFormats() {
        assertEquals(at(10, 8, 15, 30), parse("встреча в 15:30").dueAt)
        assertEquals(at(10, 8, 15, 30), parse("встреча в 15.30").dueAt)
        assertEquals(at(10, 8, 15, 30), parse("встреча 15:30").dueAt)
    }

    @Test
    fun pastTimeTodayMovesToTomorrow() {
        assertEquals(at(10, 9, 9), parse("зарядка в 9").dueAt)
    }

    @Test
    fun partOfDaySuffixes() {
        assertEquals(at(10, 8, 20), parse("ужин в 8 вечера").dueAt)
        assertEquals(at(10, 8, 13), parse("обед в 1 дня").dueAt)
        assertEquals(at(10, 9, 2), parse("рейс в 2 ночи").dueAt)
        assertEquals(at(10, 8, 20), parse("ужин в восемь вечера").dueAt)
    }

    @Test
    fun partOfDayWords() {
        assertEquals(at(10, 8, 19), parse("кино вечером").dueAt)
        assertEquals(at(10, 9, 9), parse("пробежка утром").dueAt)
        assertEquals(at(10, 8, 13), parse("встреча днём").dueAt)
        assertEquals(at(10, 8, 13), parse("встреча днем").dueAt)
        assertEquals("Встреча", parse("встреча днём").title)
    }

    @Test
    fun dayWords() {
        assertEquals(at(10, 10, 9), parse("уборка послезавтра").dueAt)
        assertEquals(at(10, 8, 11), parse("уборка сегодня").dueAt)
        assertEquals("Уборка", parse("уборка послезавтра").title)
    }

    @Test
    fun explicitDates() {
        assertEquals(at(10, 12, 9), parse("врач 12 октября").dueAt)
        assertEquals(at(10, 1, 9, year = 2027), parse("врач 1 октября").dueAt)
        assertEquals(at(10, 12, 9), parse("врач 12.10").dueAt)
        assertEquals(at(10, 12, 9, year = 2027), parse("врач 12.10.2027").dueAt)
        assertEquals(at(10, 12, 18), parse("врач 12 октября в 18").dueAt)
        assertEquals("Врач", parse("врач 12 октября в 18").title)
    }

    @Test
    fun weekdays() {
        assertEquals(at(10, 13, 9), parse("отчёт во вторник").dueAt)
        assertEquals(at(10, 8, 11), parse("отчёт в четверг").dueAt)
        assertEquals(at(10, 15, 9), parse("отчёт в чт в 9").dueAt)
        assertEquals(at(10, 9, 9), parse("отчёт в пятницу").dueAt)
        assertEquals("Отчёт", parse("отчёт во вторник").title)
    }

    @Test
    fun relativeOffsets() {
        assertEquals(at(10, 8, 12, 20), parse("перезвонить через 2 часа").dueAt)
        assertEquals(at(10, 8, 11, 20), parse("перезвонить через час").dueAt)
        assertEquals(at(10, 8, 10, 35), parse("чайник через 15 минут").dueAt)
        assertEquals(at(10, 11, 9), parse("полить цветы через три дня").dueAt)
        assertEquals(at(10, 15, 9), parse("полить цветы через неделю").dueAt)
        assertEquals("Перезвонить", parse("перезвонить через 2 часа").title)
    }

    @Test
    fun rangeWithBothDays() {
        val result = parse("Отпуск с 12 по 20 октября")
        assertEquals("Отпуск", result.title)
        assertEquals(at(10, 12, 9), result.dueAt)
        assertEquals(LocalDate.of(2026, 10, 20), result.endDate)
    }

    @Test
    fun rangeAcrossYear() {
        val result = parse("каникулы с 28 декабря по 3 января")
        assertEquals(at(12, 28, 9), result.dueAt)
        assertEquals(LocalDate.of(2027, 1, 3), result.endDate)
    }

    @Test
    fun durationInDays() {
        val result = parse("Поездка на 3 дня")
        assertEquals("Поездка", result.title)
        assertEquals(at(10, 8, 11), result.dueAt)
        assertEquals(LocalDate.of(2026, 10, 10), result.endDate)
        assertNull(parse("Поездка на 1 день").endDate)
        assertEquals(LocalDate.of(2026, 10, 13), parse("Сборы завтра на 5 дней").endDate)
    }

    @Test
    fun colors() {
        assertEquals(TaskColors.palette[5], parse("встреча красным").color)
        assertEquals(TaskColors.palette[3], parse("Зелёный проект").color)
        assertEquals(TaskColors.palette[1], parse("дедлайн синий").color)
        assertEquals("Встреча", parse("встреча красным").title)
    }

    @Test
    fun numbersWithoutPrepositionStayInTitle() {
        assertEquals(ParsedTask("Позвонить 2 раза маме", null, null, null), parse("Позвонить 2 раза маме"))
    }

    @Test
    fun onlyDateGivesEmptyTitle() {
        val result = parse("завтра в 15")
        assertEquals("", result.title)
        assertEquals(at(10, 9, 15), result.dueAt)
    }

    @Test
    fun caseAndYoInsensitive() {
        assertEquals(at(10, 9, 9), parse("ЗАВТРА").dueAt)
        assertEquals(TaskColors.palette[3], parse("ЗЕЛЕНЫМ").color)
    }

    @Test
    fun invalidTimeIsLeftInTitle() {
        val result = parse("код 25:99")
        assertNull(result.dueAt)
        assertEquals("Код 25:99", result.title)
    }

    @Test
    fun toTaskConvertsFields() {
        val parsed = ParsedTask("Отпуск", at(10, 12, 9), LocalDate.of(2026, 10, 20), null)
        val task = parsed.toTask(ZoneOffset.UTC)
        assertEquals("Отпуск", task.title)
        assertEquals(at(10, 12, 9).toInstant(ZoneOffset.UTC).toEpochMilli(), task.dueAt)
        assertEquals(LocalDate.of(2026, 10, 20).toEpochDay(), task.endDay)
        assertEquals(TaskColors.DEFAULT, task.color)
    }
}

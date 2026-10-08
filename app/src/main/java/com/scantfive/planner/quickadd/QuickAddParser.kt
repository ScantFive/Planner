package com.scantfive.planner.quickadd

import com.scantfive.planner.data.Task
import com.scantfive.planner.data.TaskColors
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Результат разбора фразы. Пустое [title] означает, что название не распознано. */
data class ParsedTask(
    val title: String,
    val dueAt: LocalDateTime?,
    val endDate: LocalDate?,
    val color: Int?,
)

fun ParsedTask.toTask(zone: ZoneId = ZoneId.systemDefault()): Task = Task(
    title = title,
    dueAt = dueAt?.atZone(zone)?.toInstant()?.toEpochMilli(),
    endDay = endDate?.toEpochDay(),
    color = color ?: TaskColors.DEFAULT,
)

/**
 * Разбор фразы на русском: «Созвон с Аней завтра в 15», «Отпуск с 12 по 20 октября, зелёный».
 * Распознанные фрагменты вырезаются, остаток становится названием.
 */
object QuickAddParser {
    // Границы слова, работающие для кириллицы (\b в Java/Kotlin regex — только ASCII).
    private const val L = "(?<![\\p{L}\\d])"
    private const val R = "(?![\\p{L}\\d])"

    private val MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val MONTH = MONTHS.joinToString("|", "(", ")")

    private val NUMBER_WORDS: Map<String, Int> = buildMap {
        fun put(value: Int, vararg forms: String) = forms.forEach { put(it, value) }
        put(1, "один", "одна", "одну", "одного", "одной")
        put(2, "два", "две", "двух")
        put(3, "три", "трех")
        put(4, "четыре", "четырех")
        put(5, "пять", "пяти")
        put(6, "шесть", "шести")
        put(7, "семь", "семи")
        put(8, "восемь", "восьми")
        put(9, "девять", "девяти")
        put(10, "десять", "десяти")
    }
    private val NUM = "(\\d{1,3}|" + NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|") + ")"

    /** Основы цветов → индекс в [TaskColors.palette]. */
    private val COLOR_STEMS = listOf(
        "индиго" to 0, "син" to 1, "бирюзов" to 2, "зелен" to 3,
        "оранжев" to 4, "красн" to 5, "розов" to 6, "фиолетов" to 7,
    )

    private val WEEKDAYS = mapOf(
        "понедельник" to DayOfWeek.MONDAY, "пн" to DayOfWeek.MONDAY,
        "вторник" to DayOfWeek.TUESDAY, "вт" to DayOfWeek.TUESDAY,
        "среду" to DayOfWeek.WEDNESDAY, "среда" to DayOfWeek.WEDNESDAY, "ср" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "чт" to DayOfWeek.THURSDAY,
        "пятницу" to DayOfWeek.FRIDAY, "пятница" to DayOfWeek.FRIDAY, "пт" to DayOfWeek.FRIDAY,
        "субботу" to DayOfWeek.SATURDAY, "суббота" to DayOfWeek.SATURDAY, "сб" to DayOfWeek.SATURDAY,
        "воскресенье" to DayOfWeek.SUNDAY, "вс" to DayOfWeek.SUNDAY,
    )

    private val PREPOSITIONS = setOf("в", "во", "на", "к", "до", "с", "со", "по")

    private val COLOR_RE = Regex(
        "$L(индиго|бирюзов\\p{L}*|зелен\\p{L}*|оранжев\\p{L}*|красн\\p{L}*|розов\\p{L}*|фиолетов\\p{L}*|" +
            "син(?:ий|им|яя|ей|его|ее|юю|ем|ие|их))$R",
    )
    private val RANGE_RE = Regex(
        "${L}с\\s+(\\d{1,2})(?:\\s+$MONTH|\\.(\\d{1,2}))?\\s+по\\s+(\\d{1,2})(?:\\s+$MONTH|\\.(\\d{1,2}))?$R",
    )
    private val DURATION_RE = Regex("${L}на\\s+$NUM\\s+(?:день|дня|дней|сутки|суток)$R")
    private val RELATIVE_RE = Regex(
        "${L}через\\s+(?:$NUM\\s+)?(минуту|минуты|минут|час|часа|часов|день|дня|дней|неделю|недели|недель)$R",
    )
    private val DAY_WORD_RE = Regex("$L(послезавтра|завтра|сегодня)$R")
    private val WEEKDAY_LONG_RE = Regex(
        "$L(?:(?:в|во)\\s+)?(понедельник|вторник|среду|среда|четверг|пятницу|пятница|субботу|суббота|воскресенье)$R",
    )
    private val WEEKDAY_SHORT_RE = Regex("$L(?:в|во)\\s+(пн|вт|ср|чт|пт|сб|вс)$R")
    private val TIME_WITH_PREP_RE = Regex("${L}в\\s+(\\d{1,2})[:.](\\d{2})$R")
    private val DATE_WORD_RE = Regex("$L(\\d{1,2})\\s+$MONTH(?:\\s+(\\d{4}))?$R")
    private val DATE_NUMERIC_RE = Regex("$L(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{4}))?$R")
    private val TIME_BARE_RE = Regex("$L(\\d{1,2}):(\\d{2})$R")
    private val TIME_HOUR_RE = Regex("${L}в\\s+$NUM(?:\\s+час(?:а|ов)?)?(?:\\s+(утра|дня|вечера|ночи))?$R")
    private val NOON_RE = Regex("${L}в\\s+полдень$R")
    private val PART_OF_DAY_RE = Regex("$L(утром|днем|вечером)$R")

    fun parse(text: String, now: LocalDateTime): ParsedTask {
        val scanner = Scanner(text)
        val today = now.toLocalDate()

        val color = scanner.take(COLOR_RE) { m ->
            COLOR_STEMS.first { m.groupValues[1].startsWith(it.first) }.second.let { TaskColors.palette[it] }
        }

        var date: LocalDate? = null
        var rangeEnd: LocalDate? = null
        scanner.take(RANGE_RE) { m -> parseRange(m, today) }?.let { (start, end) ->
            date = start
            rangeEnd = end
        }

        val durationDays = scanner.take(DURATION_RE) { m -> number(m.groupValues[1]) }

        var exact: LocalDateTime? = null
        scanner.take(RELATIVE_RE) { m ->
            val n = m.groupValues[1].takeIf { it.isNotEmpty() }?.let(::number) ?: 1
            val unit = m.groupValues[2]
            when {
                unit.startsWith("мин") -> RelativeShift(now.plusMinutes(n.toLong()), null)
                unit.startsWith("час") -> RelativeShift(now.plusHours(n.toLong()), null)
                unit.startsWith("нед") -> RelativeShift(null, today.plusWeeks(n.toLong()))
                else -> RelativeShift(null, today.plusDays(n.toLong()))
            }
        }?.let { shift ->
            exact = shift.exact
            if (date == null) date = shift.date
        }

        if (date == null) {
            date = scanner.take(DAY_WORD_RE) { m ->
                when (m.groupValues[1]) {
                    "сегодня" -> today
                    "завтра" -> today.plusDays(1)
                    else -> today.plusDays(2)
                }
            }
        }

        var byWeekday = false
        if (date == null) {
            val weekday = scanner.take(WEEKDAY_LONG_RE) { WEEKDAYS[it.groupValues[1]] }
                ?: scanner.take(WEEKDAY_SHORT_RE) { WEEKDAYS[it.groupValues[1]] }
            if (weekday != null) {
                date = today.with(TemporalAdjusters.nextOrSame(weekday))
                byWeekday = true
            }
        }

        var time: LocalTime? = scanner.take(TIME_WITH_PREP_RE) { m ->
            clock(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }

        if (date == null) {
            date = scanner.take(DATE_WORD_RE) { m ->
                explicitDate(m.groupValues[1].toInt(), MONTHS.indexOf(m.groupValues[2]) + 1, m.groupValues[3], today)
            } ?: scanner.take(DATE_NUMERIC_RE) { m ->
                explicitDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], today)
            }
        }

        if (time == null) {
            time = scanner.take(TIME_BARE_RE) { m -> clock(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
                ?: scanner.take(TIME_HOUR_RE) { m -> hourWithSuffix(number(m.groupValues[1]), m.groupValues[2]) }
                ?: scanner.take(NOON_RE) { LocalTime.NOON }
                ?: scanner.take(PART_OF_DAY_RE) { m ->
                    when (m.groupValues[1]) {
                        "утром" -> LocalTime.of(9, 0)
                        "днем" -> LocalTime.of(13, 0)
                        else -> LocalTime.of(19, 0)
                    }
                }
        }

        var due: LocalDateTime? = exact ?: run {
            val day = date
            when {
                day != null && time != null -> day.atTime(time)
                day != null -> defaultTimeFor(day, now)
                time != null -> today.atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }
                durationDays != null -> defaultTimeFor(today, now)
                else -> null
            }
        }
        // «в чт в 9», сказанное в четверг после девяти, — это следующий четверг.
        if (byWeekday && due != null && !due.isAfter(now)) due = due.plusWeeks(1)
        if (due != null && !due.isAfter(now)) due = null

        val start = due?.toLocalDate()
        val end = when {
            start == null -> null
            rangeEnd != null -> rangeEnd
            durationDays != null && durationDays > 1 -> start.plusDays(durationDays - 1L)
            else -> null
        }?.takeIf { start != null && it.isAfter(start) }

        return ParsedTask(scanner.remainingTitle(), due, end, color)
    }

    private data class RelativeShift(val exact: LocalDateTime?, val date: LocalDate?)

    private fun number(token: String): Int? = token.toIntOrNull() ?: NUMBER_WORDS[token]

    private fun clock(hour: Int, minute: Int): LocalTime? =
        if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null

    private fun hourWithSuffix(hour: Int?, suffix: String): LocalTime? {
        if (hour == null) return null
        val adjusted = when (suffix) {
            "утра" -> if (hour == 12) 0 else hour
            "дня", "вечера" -> if (hour in 1..11) hour + 12 else hour
            "ночи" -> if (hour == 12) 0 else hour
            else -> hour
        }
        return clock(adjusted, 0)
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    /** Дата без года, уже прошедшая в этом году, переносится на следующий. */
    private fun explicitDate(day: Int, month: Int, year: String, today: LocalDate): LocalDate? {
        if (year.isNotEmpty()) return safeDate(year.toInt(), month, day)
        val date = safeDate(today.year, month, day) ?: return null
        return if (date.isBefore(today)) date.plusYears(1) else date
    }

    private fun monthOf(word: String, number: String): Int? = when {
        word.isNotEmpty() -> MONTHS.indexOf(word) + 1
        number.isNotEmpty() -> number.toInt()
        else -> null
    }

    private fun parseRange(m: MatchResult, today: LocalDate): Pair<LocalDate, LocalDate>? {
        val g = m.groupValues
        val endMonth = monthOf(g[5], g[6]) ?: monthOf(g[2], g[3]) ?: today.monthValue
        val startMonth = monthOf(g[2], g[3]) ?: endMonth
        var start = safeDate(today.year, startMonth, g[1].toInt()) ?: return null
        var end = safeDate(today.year, endMonth, g[4].toInt()) ?: return null
        if (start.isBefore(today)) {
            start = start.plusYears(1)
            end = end.plusYears(1)
        }
        if (end.isBefore(start)) end = end.plusYears(1)
        return start to end
    }

    /** То же правило, что у создания задачи из календаря: 09:00 или ближайший круглый час сегодня. */
    private fun defaultTimeFor(day: LocalDate, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        return when {
            day.isBefore(today) -> null
            day.isAfter(today) -> day.atTime(9, 0)
            else -> {
                val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                if (nextHour.toLocalDate() == today) nextHour else day.atTime(23, 59)
            }
        }
    }

    /** Ищет совпадения в нормализованном тексте и запоминает, какие символы уже распознаны. */
    private class Scanner(private val original: String) {
        // Посимвольная нормализация сохраняет длину строки, поэтому индексы совпадают с исходной.
        private val normalized = original.map { c -> if (c == 'ё' || c == 'Ё') 'е' else c.lowercaseChar() }
            .joinToString("")
        private val consumed = BooleanArray(original.length)

        /** Первое свободное совпадение, для которого [convert] вернул значение; оно помечается распознанным. */
        fun <T : Any> take(regex: Regex, convert: (MatchResult) -> T?): T? {
            for (match in regex.findAll(normalized)) {
                if (match.range.any { consumed[it] }) continue
                val value = convert(match) ?: continue
                match.range.forEach { consumed[it] = true }
                return value
            }
            return null
        }

        fun remainingTitle(): String {
            val rest = original.indices.joinToString("") { i -> if (consumed[i]) " " else original[i].toString() }
            val tokens = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
            fun isJunk(token: String) = token.trim(*PUNCTUATION).isEmpty() ||
                token.trim(*PUNCTUATION).lowercase() in PREPOSITIONS
            while (tokens.isNotEmpty() && isJunk(tokens.first())) tokens.removeAt(0)
            while (tokens.isNotEmpty() && isJunk(tokens.last())) tokens.removeAt(tokens.lastIndex)
            return tokens.joinToString(" ").trim(*PUNCTUATION).replaceFirstChar { it.uppercaseChar() }
        }
    }

    private val PUNCTUATION = charArrayOf(' ', ',', '.', ';', ':', '!', '?', '-', '—')
}

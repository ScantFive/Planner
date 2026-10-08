package com.scantfive.planner.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.scantfive.planner.ui.calendar.BarSegment
import com.scantfive.planner.ui.calendar.CalendarEvent
import com.scantfive.planner.ui.calendar.MAX_DOTS
import com.scantfive.planner.ui.calendar.MAX_LANES
import com.scantfive.planner.ui.calendar.dotsFor
import com.scantfive.planner.ui.calendar.heatAlpha
import com.scantfive.planner.ui.calendar.heatLevel
import com.scantfive.planner.ui.calendar.monthWeeks
import com.scantfive.planner.ui.calendar.weekSegments
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Цвета виджета (ARGB). */
data class WidgetPalette(
    val text: Int,
    val muted: Int,
    val accent: Int,
    val onAccent: Int,
    val heat: Int,
)

/** Рисует месяц той же раскладкой, что и календарь в приложении: точки и полосы событий. */
class MonthBitmapRenderer(private val density: Float) {
    private fun dp(value: Float) = value * density

    fun render(
        month: YearMonth,
        today: LocalDate,
        events: List<CalendarEvent>,
        firstDayOfWeek: DayOfWeek,
        widthPx: Int,
        heightPx: Int,
        palette: WidgetPalette,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val weeks = monthWeeks(month, firstDayOfWeek)
        val columnWidth = bitmap.width / 7f
        val headerHeight = dp(16f)
        val rowHeight = (bitmap.height - headerHeight) / weeks.size

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = dp(11f)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        text.color = palette.muted
        for (column in 0 until 7) {
            val label = firstDayOfWeek.plus(column.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            canvas.drawText(label, columnWidth * (column + 0.5f), headerHeight - dp(4f), text)
        }

        // Вертикальная раскладка строки: номер дня, дорожки полос, точки внизу.
        val laneTopOffset = dp(20f)
        val laneHeight = dp(4f)
        val laneGap = dp(1.5f)
        val dotsArea = dp(8f)
        val fittingLanes = ((rowHeight - laneTopOffset - dotsArea + laneGap) / (laneHeight + laneGap)).toInt()
        val lanes = fittingLanes.coerceIn(0, MAX_LANES)

        weeks.forEachIndexed { row, week ->
            val top = headerHeight + row * rowHeight
            week.forEachIndexed { column, day ->
                val level = heatLevel(day, events)
                if (level > 0) {
                    fill.color = withAlpha(palette.heat, heatAlpha(level))
                    val inset = dp(1f)
                    canvas.drawRoundRect(
                        RectF(column * columnWidth + inset, top + inset, (column + 1) * columnWidth - inset, top + rowHeight - inset),
                        dp(6f),
                        dp(6f),
                        fill,
                    )
                }
            }
            week.forEachIndexed { column, day ->
                drawDayNumber(canvas, day, month, today, columnWidth * (column + 0.5f), top + dp(10f), text, fill, palette)
            }
            weekSegments(week, events).filter { it.lane < lanes }.forEach { segment ->
                val laneTop = top + laneTopOffset + segment.lane * (laneHeight + laneGap)
                drawBar(canvas, segment, columnWidth, laneTop, laneHeight, fill)
            }
            week.forEachIndexed { column, day ->
                drawDots(canvas, dotsFor(day, events).take(MAX_DOTS), columnWidth * (column + 0.5f), top + rowHeight - dp(4f), fill)
            }
        }
        return bitmap
    }

    private fun drawDayNumber(
        canvas: Canvas,
        day: LocalDate,
        month: YearMonth,
        today: LocalDate,
        centerX: Float,
        centerY: Float,
        text: Paint,
        fill: Paint,
        palette: WidgetPalette,
    ) {
        val isToday = day == today
        if (isToday) {
            fill.color = palette.accent
            canvas.drawCircle(centerX, centerY, dp(8.5f), fill)
        }
        text.color = when {
            isToday -> palette.onAccent
            YearMonth.from(day) == month -> palette.text
            else -> palette.muted
        }
        text.typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val baseline = centerY - (text.ascent() + text.descent()) / 2
        canvas.drawText(day.dayOfMonth.toString(), centerX, baseline, text)
    }

    private fun drawBar(canvas: Canvas, segment: BarSegment, columnWidth: Float, top: Float, height: Float, fill: Paint) {
        val inset = dp(2f)
        val left = segment.startCol * columnWidth + if (segment.roundStart) inset else 0f
        val right = (segment.endCol + 1) * columnWidth - if (segment.roundEnd) inset else 0f
        val radius = height / 2
        fill.color = eventColor(segment.event)
        canvas.drawRoundRect(RectF(left, top, right, top + height), radius, radius, fill)
        // Полоса продолжается за край недели — этот край без скругления.
        if (!segment.roundStart) canvas.drawRect(left, top, left + radius, top + height, fill)
        if (!segment.roundEnd) canvas.drawRect(right - radius, top, right, top + height, fill)
    }

    private fun drawDots(canvas: Canvas, dayEvents: List<CalendarEvent>, centerX: Float, centerY: Float, fill: Paint) {
        if (dayEvents.isEmpty()) return
        val step = dp(5f)
        val startX = centerX - step * (dayEvents.size - 1) / 2
        dayEvents.forEachIndexed { index, event ->
            fill.color = eventColor(event)
            canvas.drawCircle(startX + index * step, centerY, dp(2f), fill)
        }
    }

    /** Выполненные события приглушены, как в приложении. */
    private fun eventColor(event: CalendarEvent): Int =
        if (event.isDone) withAlpha(event.color, 0.4f) else event.color

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)
}

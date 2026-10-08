package com.scantfive.planner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.scantfive.planner.ACTION_QUICK_ADD
import com.scantfive.planner.MainActivity
import com.scantfive.planner.PlannerApp
import com.scantfive.planner.R
import com.scantfive.planner.data.TaskColors
import com.scantfive.planner.ui.calendar.CalendarEvent
import com.scantfive.planner.ui.calendar.monthTitle
import com.scantfive.planner.ui.calendar.toCalendarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.sqrt

/** Виджет: календарь текущего месяца и кнопка голосового быстрого добавления. */
class CalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAsync(context, manager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateAsync(context, manager, intArrayOf(appWidgetId))
    }

    private fun updateAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext as PlannerApp
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val events = app.container.repository.allTasks().mapNotNull { it.toCalendarEvent() }
                ids.forEach { id -> manager.updateAppWidget(id, buildViews(app, manager, id, events)) }
            } catch (e: Exception) {
                Log.e(TAG, "Widget update failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, manager: AppWidgetManager, id: Int, events: List<CalendarEvent>): RemoteViews {
        val options = manager.getAppWidgetOptions(id)
        val density = context.resources.displayMetrics.density
        // В портретной ориентации виджет занимает минимальную ширину и максимальную высоту из опций.
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
        var width = (widthDp - 2 * PADDING_DP) * density
        var height = (heightDp - HEADER_DP - 2 * PADDING_DP) * density
        var scale = 1f
        if (width * height > MAX_PIXELS) {
            scale = sqrt(MAX_PIXELS / (width * height))
            width *= scale
            height *= scale
        }

        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val bitmap = MonthBitmapRenderer(density * scale).render(
            month = month,
            today = today,
            events = events,
            firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
            widthPx = width.toInt(),
            heightPx = height.toInt(),
            palette = palette(context),
        )

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val quickAdd = PendingIntent.getActivity(
            context,
            REQUEST_QUICK_ADD,
            Intent(context, MainActivity::class.java).setAction(ACTION_QUICK_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteViews(context.packageName, R.layout.widget_calendar).apply {
            setTextViewText(R.id.widget_title, monthTitle(month))
            setImageViewBitmap(R.id.widget_image, bitmap)
            setOnClickPendingIntent(R.id.widget_title, openApp)
            setOnClickPendingIntent(R.id.widget_image, openApp)
            setOnClickPendingIntent(R.id.widget_mic, quickAdd)
        }
    }

    private fun palette(context: Context): WidgetPalette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            WidgetPalette(text = 0xFFE6E1E5.toInt(), muted = 0x80E6E1E5.toInt(), accent = 0xFF9FA8DA.toInt(),
                onAccent = 0xFF1C1B1F.toInt(), heat = 0xFF9FA8DA.toInt())
        } else {
            WidgetPalette(text = 0xFF1C1B1F.toInt(), muted = 0x801C1B1F.toInt(), accent = TaskColors.DEFAULT,
                onAccent = 0xFFFFFFFF.toInt(), heat = TaskColors.DEFAULT)
        }
    }

    private companion object {
        const val TAG = "CalendarWidget"
        const val REQUEST_OPEN = 0
        const val REQUEST_QUICK_ADD = 1
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180
        const val HEADER_DP = 40
        const val PADDING_DP = 8

        /** Ограничение размера картинки: RemoteViews передаёт её между процессами. */
        const val MAX_PIXELS = 1_000_000f
    }
}

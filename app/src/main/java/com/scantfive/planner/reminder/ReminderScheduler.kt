package com.scantfive.planner.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.scantfive.planner.data.Task

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Ставит будильник на срок задачи или снимает его, если напоминание больше не нужно. */
    fun sync(task: Task, now: Long = System.currentTimeMillis()) {
        if (!task.needsReminder(now)) {
            cancel(task.id)
            return
        }
        val triggerAt = task.dueAt ?: return
        val pendingIntent = pendingIntent(task.id, create = true) ?: return
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            // Разрешение на точные будильники отозвано между проверкой и вызовом.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** Снимает будильник и убирает уже показанное уведомление. */
    fun cancel(taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
        pendingIntent(taskId, create = false)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(taskId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            // data делает Intent уникальным для каждой задачи (extras при сравнении не учитываются).
            data = Uri.parse("planner://task/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
        }
        var flags = PendingIntent.FLAG_IMMUTABLE
        flags = flags or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}

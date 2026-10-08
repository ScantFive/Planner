package com.scantfive.planner.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scantfive.planner.PlannerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Срабатывает по будильнику и показывает уведомление о задаче. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        val pending = goAsync()
        val app = context.applicationContext as PlannerApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = app.container.repository.get(taskId)
                if (task != null && !task.isDone) {
                    NotificationHelper.show(app, task)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

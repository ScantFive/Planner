package com.scantfive.planner.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scantfive.planner.PlannerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Будильники сбрасываются при перезагрузке и обновлении приложения — ставим заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val app = context.applicationContext as PlannerApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = app.container.repository
                repository.rescheduleAll()
                repository.missedReminders().forEach {
                    NotificationHelper.show(app, it)
                    repository.markFired(it.id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

package com.scantfive.planner

import android.app.Application
import android.content.Context
import com.scantfive.planner.data.PlannerDatabase
import com.scantfive.planner.data.TaskRepository
import com.scantfive.planner.reminder.NotificationHelper
import com.scantfive.planner.reminder.ReminderScheduler

/** Ручной DI-контейнер: для MVP этого достаточно. */
class AppContainer(context: Context) {
    private val database = PlannerDatabase.create(context)
    val repository = TaskRepository(database.taskDao(), ReminderScheduler(context))
}

class PlannerApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}

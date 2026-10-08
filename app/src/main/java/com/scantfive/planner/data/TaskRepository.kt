package com.scantfive.planner.data

import com.scantfive.planner.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val dao: TaskDao,
    private val scheduler: ReminderScheduler,
) {
    val tasks: Flow<List<Task>> = dao.observeAll()

    suspend fun get(id: Long): Task? = dao.get(id)

    /** Сохраняет задачу и синхронизирует напоминание. Возвращает id задачи. */
    suspend fun save(task: Task): Long {
        val previousDue = if (task.id == 0L) null else dao.get(task.id)?.dueAt
        val toSave = if (task.dueAt != previousDue) task.copy(reminderFired = false) else task
        val inserted = dao.upsert(toSave)
        val id = if (task.id == 0L) inserted else task.id
        scheduler.sync(task.copy(id = id))
        return id
    }

    suspend fun setDone(task: Task, done: Boolean) {
        save(task.copy(isDone = done))
    }

    suspend fun delete(task: Task) {
        scheduler.cancel(task.id)
        dao.delete(task.id)
    }

    /** Пересоздаёт все будущие напоминания (после перезагрузки устройства). */
    suspend fun rescheduleAll() {
        dao.getUpcoming(System.currentTimeMillis()).forEach { scheduler.sync(it) }
    }

    /** Задачи, чей срок прошёл без уведомления. Вызывающий показывает их и вызывает [markFired]. */
    suspend fun missedReminders(): List<Task> = dao.getMissed(System.currentTimeMillis())

    suspend fun markFired(id: Long) = dao.markFired(id)
}

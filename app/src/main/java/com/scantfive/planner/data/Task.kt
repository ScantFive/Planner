package com.scantfive.planner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** Срок выполнения (epoch millis) или null, если срок не задан. */
    val dueAt: Long? = null,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Уведомление по сроку уже показано (нужно, чтобы не повторять и не терять пропущенные). */
    @ColumnInfo(defaultValue = "0") val reminderFired: Boolean = false,
    /** Цвет события в календаре (ARGB). */
    @ColumnInfo(defaultValue = "-12627531") val color: Int = TaskColors.DEFAULT,
    /** Последний день протяжённого события (epoch day) или null, если событие однодневное. */
    val endDay: Long? = null,
) {
    /** Нужно ли напомнить об этой задаче: не выполнена и срок ещё впереди. */
    fun needsReminder(now: Long): Boolean = !isDone && dueAt != null && dueAt > now

    /** Срок наступил; допускаем срабатывание на минуту раньше срока. */
    fun isDue(now: Long): Boolean = !isDone && dueAt != null && dueAt <= now + DUE_SLACK_MS

    fun isOverdue(now: Long): Boolean = !isDone && dueAt != null && dueAt <= now

    companion object {
        const val DUE_SLACK_MS = 60_000L
    }
}

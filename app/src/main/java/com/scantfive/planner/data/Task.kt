package com.scantfive.planner.data

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
) {
    /** Нужно ли напомнить об этой задаче: не выполнена и срок ещё впереди. */
    fun needsReminder(now: Long): Boolean = !isDone && dueAt != null && dueAt > now

    fun isOverdue(now: Long): Boolean = !isDone && dueAt != null && dueAt <= now
}

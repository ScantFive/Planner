package com.scantfive.planner.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        "SELECT * FROM tasks ORDER BY isDone ASC, " +
            "CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END ASC, dueAt ASC, createdAt DESC"
    )
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND dueAt IS NOT NULL AND dueAt > :now")
    suspend fun getUpcoming(now: Long): List<Task>

    /** Возвращает id вставленной строки (для обновления результат не используется). */
    @Upsert
    suspend fun upsert(task: Task): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

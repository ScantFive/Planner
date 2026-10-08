package com.scantfive.planner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): PlannerDatabase =
            Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db").build()
    }
}

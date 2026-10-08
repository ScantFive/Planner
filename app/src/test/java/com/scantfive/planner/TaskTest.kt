package com.scantfive.planner

import com.scantfive.planner.data.Task
import com.scantfive.planner.ui.TaskFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTest {
    private val now = 1_000_000L

    @Test
    fun futureActiveTaskNeedsReminder() {
        assertTrue(Task(title = "a", dueAt = now + 1).needsReminder(now))
    }

    @Test
    fun noReminderWithoutDueDate() {
        assertFalse(Task(title = "a").needsReminder(now))
    }

    @Test
    fun noReminderForPastOrDoneTask() {
        assertFalse(Task(title = "a", dueAt = now - 1).needsReminder(now))
        assertFalse(Task(title = "a", dueAt = now + 1, isDone = true).needsReminder(now))
    }

    @Test
    fun overdueOnlyForActiveTasksWithPastDue() {
        assertTrue(Task(title = "a", dueAt = now - 1).isOverdue(now))
        assertFalse(Task(title = "a", dueAt = now - 1, isDone = true).isOverdue(now))
        assertFalse(Task(title = "a").isOverdue(now))
    }

    @Test
    fun dueWhenTimeReachedAndNotDone() {
        assertTrue(Task(title = "a", dueAt = now - 1).isDue(now))
        assertTrue(Task(title = "a", dueAt = now + Task.DUE_SLACK_MS).isDue(now))
        assertFalse(Task(title = "a", dueAt = now + Task.DUE_SLACK_MS + 1).isDue(now))
        assertFalse(Task(title = "a", dueAt = now - 1, isDone = true).isDue(now))
        assertFalse(Task(title = "a").isDue(now))
    }

    @Test
    fun filtersSplitTasksByDoneFlag() {
        val active = Task(id = 1, title = "a")
        val done = Task(id = 2, title = "b", isDone = true)
        val all = listOf(active, done)
        assertEquals(listOf(active), TaskFilter.ACTIVE.apply(all))
        assertEquals(listOf(done), TaskFilter.DONE.apply(all))
        assertEquals(all, TaskFilter.ALL.apply(all))
    }
}

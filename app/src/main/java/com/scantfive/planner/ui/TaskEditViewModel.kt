package com.scantfive.planner.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantfive.planner.data.Task
import com.scantfive.planner.data.TaskColors
import com.scantfive.planner.data.TaskRepository
import com.scantfive.planner.ui.calendar.defaultDueMillis
import com.scantfive.planner.ui.calendar.epochDayOf
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/** [taskId] == [NEW_TASK] — создание новой задачи. */
class TaskEditViewModel(
    private val repository: TaskRepository,
    private val taskId: Long,
    /** Для новой задачи: день (epoch day), выбранный в календаре. */
    initialDay: Long? = null,
    /** Для новой задачи: заполненный черновик из быстрого добавления. */
    draft: Task? = null,
) : ViewModel() {
    var title by mutableStateOf("")
    var notes by mutableStateOf("")
    var dueAt by mutableStateOf<Long?>(null)
        private set
    var endDay by mutableStateOf<Long?>(null)
    var color by mutableIntStateOf(TaskColors.DEFAULT)
    var loaded by mutableStateOf(taskId == NEW_TASK)
        private set

    /** Редактируемой задачи больше нет (например, удалена) — экран нужно закрыть. */
    var missing by mutableStateOf(false)
        private set

    /** Защита от повторных нажатий, пока операция сохранения/удаления выполняется. */
    private var busy = false

    private var original: Task? = null

    init {
        if (taskId == NEW_TASK && draft != null) {
            title = draft.title
            notes = draft.notes
            dueAt = draft.dueAt
            endDay = draft.endDay
            color = draft.color
        } else if (taskId == NEW_TASK && initialDay != null) {
            dueAt = defaultDueMillis(LocalDate.ofEpochDay(initialDay), LocalDateTime.now())
        }
        if (taskId != NEW_TASK) {
            viewModelScope.launch {
                val task = repository.get(taskId)
                if (task == null) {
                    missing = true
                } else {
                    original = task
                    title = task.title
                    notes = task.notes
                    dueAt = task.dueAt
                    endDay = task.endDay
                    color = task.color
                }
                loaded = true
            }
        }
    }

    /** Меняет срок и согласует с ним дату окончания (она не может быть раньше начала). */
    fun updateDue(value: Long?) {
        dueAt = value
        val end = endDay
        if (value == null || (end != null && end <= epochDayOf(value))) endDay = null
    }

    val canSave: Boolean get() = title.isNotBlank()

    fun save(onDone: () -> Unit) {
        if (!canSave || busy) return
        busy = true
        viewModelScope.launch {
            val base = original ?: Task(title = "")
            repository.save(
                base.copy(
                    title = title.trim(),
                    notes = notes.trim(),
                    dueAt = dueAt,
                    endDay = endDay,
                    color = color,
                ),
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val task = original ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            repository.delete(task)
            onDone()
        }
    }

    companion object {
        const val NEW_TASK = -1L
    }
}

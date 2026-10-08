package com.scantfive.planner.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantfive.planner.data.Task
import com.scantfive.planner.data.TaskRepository
import kotlinx.coroutines.launch

/** [taskId] == [NEW_TASK] — создание новой задачи. */
class TaskEditViewModel(
    private val repository: TaskRepository,
    private val taskId: Long,
) : ViewModel() {
    var title by mutableStateOf("")
    var notes by mutableStateOf("")
    var dueAt by mutableStateOf<Long?>(null)
    var loaded by mutableStateOf(taskId == NEW_TASK)
        private set

    /** Редактируемой задачи больше нет (например, удалена) — экран нужно закрыть. */
    var missing by mutableStateOf(false)
        private set

    /** Защита от повторных нажатий, пока операция сохранения/удаления выполняется. */
    private var busy = false

    private var original: Task? = null

    init {
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
                }
                loaded = true
            }
        }
    }

    val canSave: Boolean get() = title.isNotBlank()

    fun save(onDone: () -> Unit) {
        if (!canSave || busy) return
        busy = true
        viewModelScope.launch {
            val base = original ?: Task(title = "")
            repository.save(base.copy(title = title.trim(), notes = notes.trim(), dueAt = dueAt))
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

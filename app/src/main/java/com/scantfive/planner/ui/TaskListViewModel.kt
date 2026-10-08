package com.scantfive.planner.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantfive.planner.R
import com.scantfive.planner.data.Task
import com.scantfive.planner.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter(@StringRes val labelRes: Int) {
    ACTIVE(R.string.filter_active),
    DONE(R.string.filter_done),
    ALL(R.string.filter_all);

    fun apply(tasks: List<Task>): List<Task> = when (this) {
        ACTIVE -> tasks.filter { !it.isDone }
        DONE -> tasks.filter { it.isDone }
        ALL -> tasks
    }
}

class TaskListViewModel(private val repository: TaskRepository) : ViewModel() {
    private val _filter = MutableStateFlow(TaskFilter.ACTIVE)
    val filter: StateFlow<TaskFilter> = _filter

    val tasks: StateFlow<List<Task>> = combine(repository.tasks, _filter) { all, filter ->
        filter.apply(all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch { repository.setDone(task, !task.isDone) }
    }
}

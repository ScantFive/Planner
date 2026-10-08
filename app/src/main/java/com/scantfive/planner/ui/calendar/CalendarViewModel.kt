package com.scantfive.planner.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantfive.planner.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(repository: TaskRepository) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selected = MutableStateFlow(LocalDate.now())
    val selected: StateFlow<LocalDate> = _selected

    val events: StateFlow<List<CalendarEvent>> = repository.tasks
        .map { tasks -> tasks.mapNotNull { it.toCalendarEvent() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun goToToday() {
        _selected.value = LocalDate.now()
        _month.value = YearMonth.now()
    }

    /** Выбор дня; нажатие на день соседнего месяца переключает отображаемый месяц. */
    fun select(day: LocalDate) {
        _selected.value = day
        _month.value = YearMonth.from(day)
    }
}

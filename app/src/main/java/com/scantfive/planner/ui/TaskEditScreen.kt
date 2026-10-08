package com.scantfive.planner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scantfive.planner.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    viewModel: TaskEditViewModel,
    isNew: Boolean,
    onClose: () -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var pickedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var timeInPastError by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel.missing) {
        if (viewModel.missing) onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isNew) R.string.title_new_task else R.string.title_edit_task))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { viewModel.delete(onClose) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!viewModel.loaded) return@Scaffold
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text(stringResource(R.string.field_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            val due = viewModel.dueAt
            Text(
                if (due == null) {
                    stringResource(R.string.due_none)
                } else {
                    stringResource(R.string.due_set, formatDateTime(due))
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(stringResource(if (due == null) R.string.due_pick else R.string.due_change))
                }
                if (due != null) {
                    TextButton(onClick = { viewModel.dueAt = null }) {
                        Text(stringResource(R.string.due_clear))
                    }
                }
            }

            Button(
                onClick = { viewModel.save(onClose) },
                enabled = viewModel.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val initial = viewModel.dueAt
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?.takeIf { !it.isBefore(today) }
            ?: today
        val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtcMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            // DatePicker отдаёт полночь UTC выбранной даты.
                            pickedEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        timeInPastError = false
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val date = pickedEpochDay?.let { LocalDate.ofEpochDay(it) }
        val now = LocalDateTime.now()
        val suggested = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        val initial = viewModel.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            // Ближайший «круглый» час; если он уже завтра, а выбрана сегодняшняя дата — конец дня.
            ?: if (date == now.toLocalDate() && suggested.toLocalDate() != date) {
                LocalTime.of(23, 59)
            } else {
                suggested.toLocalTime()
            }
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (date != null) {
                            val due = LocalDateTime.of(date, LocalTime.of(state.hour, state.minute))
                                .atZone(zone).toInstant().toEpochMilli()
                            if (due <= System.currentTimeMillis()) {
                                timeInPastError = true
                            } else {
                                viewModel.dueAt = due
                                timeInPastError = false
                                showTimePicker = false
                            }
                        } else {
                            showTimePicker = false
                        }
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = {
                Column {
                    TimePicker(state = state)
                    if (timeInPastError) {
                        Text(
                            stringResource(R.string.error_time_in_past),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
    }
}

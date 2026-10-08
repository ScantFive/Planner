package com.scantfive.planner.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scantfive.planner.R
import com.scantfive.planner.data.TaskColors
import com.scantfive.planner.ui.formatDateTime
import com.scantfive.planner.ui.formatDay
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Быстрое добавление одной фразой. При [startWithVoice] сразу открывается распознавание речи.
 * [onSave] и [onDetails] получают разбор, сделанный в момент нажатия.
 */
@Composable
fun QuickAddDialog(
    startWithVoice: Boolean,
    onDismiss: () -> Unit,
    onSave: (ParsedTask) -> Unit,
    onDetails: (ParsedTask) -> Unit,
) {
    val context = LocalContext.current
    val speechAvailable = remember { isSpeechAvailable(context) }
    var text by rememberSaveable { mutableStateOf("") }
    // Не перезапускать распознавание после поворота экрана.
    var voiceStarted by rememberSaveable { mutableStateOf(false) }
    val speech = rememberSpeechLauncher { recognized -> text = recognized }
    val parsed = remember(text) { QuickAddParser.parse(text, LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        if (startWithVoice && speechAvailable && !voiceStarted) {
            voiceStarted = true
            speech.launch()
        }
    }

    fun parseNow() = QuickAddParser.parse(text, LocalDateTime.now())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                    trailingIcon = if (speechAvailable) {
                        {
                            IconButton(onClick = { speech.launch() }) {
                                Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.voice_input))
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                ParsedPreview(parsed)
            }
        },
        confirmButton = {
            TextButton(enabled = parsed.title.isNotBlank(), onClick = { onSave(parseNow()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(enabled = text.isNotBlank(), onClick = { onDetails(parseNow()) }) {
                    Text(stringResource(R.string.quick_add_details))
                }
            }
        },
    )
}

@Composable
private fun ParsedPreview(parsed: ParsedTask) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(Color(parsed.color ?: TaskColors.DEFAULT), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                parsed.title.ifBlank { stringResource(R.string.quick_add_no_title) },
                style = MaterialTheme.typography.bodyLarge,
            )
            val due = parsed.dueAt
            Text(
                if (due == null) {
                    stringResource(R.string.due_none)
                } else {
                    val millis = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val end = parsed.endDate
                    if (end == null) formatDateTime(millis) else "${formatDateTime(millis)} – ${formatDay(end.toEpochDay())}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

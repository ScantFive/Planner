package com.scantfive.planner.quickadd

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.scantfive.planner.R

/** Кнопка быстрого добавления для верхней панели: микрофон, если распознавание доступно. */
@Composable
fun QuickAddAction(onClick: () -> Unit) {
    val context = LocalContext.current
    val speechAvailable = remember { isSpeechAvailable(context) }
    IconButton(onClick = onClick) {
        if (speechAvailable) {
            Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.voice_input))
        } else {
            Icon(Icons.Default.Create, stringResource(R.string.quick_add))
        }
    }
}

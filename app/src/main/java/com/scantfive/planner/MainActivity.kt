package com.scantfive.planner

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import com.scantfive.planner.ui.calendar.CalendarScreen
import com.scantfive.planner.ui.calendar.CalendarViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scantfive.planner.quickadd.QuickAddDialog
import com.scantfive.planner.quickadd.toTask
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scantfive.planner.reminder.NotificationHelper
import com.scantfive.planner.ui.TaskEditScreen
import com.scantfive.planner.ui.TaskEditViewModel
import com.scantfive.planner.ui.TaskListScreen
import com.scantfive.planner.ui.TaskListViewModel
import com.scantfive.planner.ui.theme.PlannerTheme

/** Открыть приложение сразу в быстром добавлении голосом (кнопка микрофона в виджете). */
const val ACTION_QUICK_ADD = "com.scantfive.planner.action.QUICK_ADD"

class MainActivity : ComponentActivity() {
    /** Счётчик запросов быстрого добавления извне; каждое увеличение открывает окно. */
    private val quickAddRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // После поворота экрана тот же intent не должен открывать окно повторно.
        if (savedInstanceState == null && intent?.action == ACTION_QUICK_ADD) quickAddRequests.intValue++
        setContent {
            PlannerTheme {
                RequestNotificationPermission()
                PlannerNavHost(quickAddRequests.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_QUICK_ADD) quickAddRequests.intValue++
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, NotificationHelper.permission) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(NotificationHelper.permission)
    }
}

private object Routes {
    const val CALENDAR = "calendar"
    const val TASKS = "tasks"
    const val EDIT = "edit/{id}?day={day}"
    const val NO_DAY = -1L

    fun edit(id: Long, day: Long = NO_DAY) = "edit/$id?day=$day"
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private const val QUICK_ADD_CLOSED = 0
private const val QUICK_ADD_VOICE = 2

@Composable
private fun PlannerNavHost(quickAddRequests: Int) {
    val context = LocalContext.current
    val container = (context.applicationContext as PlannerApp).container
    val repository = container.repository
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var quickAdd by rememberSaveable { mutableIntStateOf(QUICK_ADD_CLOSED) }

    LaunchedEffect(quickAddRequests) {
        if (quickAddRequests > 0) quickAdd = QUICK_ADD_VOICE
    }

    if (quickAdd != QUICK_ADD_CLOSED) {
        QuickAddDialog(
            startWithVoice = quickAdd == QUICK_ADD_VOICE,
            onDismiss = { quickAdd = QUICK_ADD_CLOSED },
            onSave = { parsed ->
                quickAdd = QUICK_ADD_CLOSED
                scope.launch { repository.save(parsed.toTask()) }
                Toast.makeText(context, R.string.task_added, Toast.LENGTH_SHORT).show()
            },
            onDetails = { parsed ->
                quickAdd = QUICK_ADD_CLOSED
                container.draft.put(parsed.toTask())
                navController.navigate(Routes.edit(TaskEditViewModel.NEW_TASK))
            },
        )
    }
    val tabs = listOf(
        Tab(Routes.CALENDAR, R.string.tab_calendar, Icons.Default.DateRange),
        Tab(Routes.TASKS, R.string.title_tasks, Icons.AutoMirrored.Filled.List),
    )
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        // Отступы системных панелей обрабатывают вложенные экраны и NavigationBar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.CALENDAR) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALENDAR,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CALENDAR) {
                val vm: CalendarViewModel = viewModel(
                    factory = viewModelFactory { initializer { CalendarViewModel(repository) } },
                )
                CalendarScreen(
                    viewModel = vm,
                    onAddTask = { day ->
                        navController.navigate(Routes.edit(TaskEditViewModel.NEW_TASK, day.toEpochDay()))
                    },
                    onOpenTask = { id -> navController.navigate(Routes.edit(id)) },
                    onQuickAdd = { quickAdd = QUICK_ADD_VOICE },
                )
            }
            composable(Routes.TASKS) {
                val vm: TaskListViewModel = viewModel(
                    factory = viewModelFactory { initializer { TaskListViewModel(repository) } },
                )
                TaskListScreen(
                    viewModel = vm,
                    onAddTask = { navController.navigate(Routes.edit(TaskEditViewModel.NEW_TASK)) },
                    onOpenTask = { id -> navController.navigate(Routes.edit(id)) },
                    onQuickAdd = { quickAdd = QUICK_ADD_VOICE },
                )
            }
            composable(
                route = Routes.EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("day") {
                        type = NavType.LongType
                        defaultValue = Routes.NO_DAY
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: TaskEditViewModel.NEW_TASK
                val day = entry.arguments?.getLong("day")?.takeIf { it != Routes.NO_DAY }
                val vm: TaskEditViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val draft = if (id == TaskEditViewModel.NEW_TASK) container.draft.take() else null
                            TaskEditViewModel(repository, id, day, draft)
                        }
                    },
                )
                TaskEditScreen(
                    viewModel = vm,
                    isNew = id == TaskEditViewModel.NEW_TASK,
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}

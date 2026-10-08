package com.scantfive.planner

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlannerTheme {
                RequestNotificationPermission()
                PlannerNavHost()
            }
        }
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

@Composable
private fun PlannerNavHost() {
    val repository = (LocalContext.current.applicationContext as PlannerApp).container.repository
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            val vm: TaskListViewModel = viewModel(
                factory = viewModelFactory { initializer { TaskListViewModel(repository) } },
            )
            TaskListScreen(
                viewModel = vm,
                onAddTask = { navController.navigate("edit/${TaskEditViewModel.NEW_TASK}") },
                onOpenTask = { id -> navController.navigate("edit/$id") },
            )
        }
        composable(
            route = "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: TaskEditViewModel.NEW_TASK
            val vm: TaskEditViewModel = viewModel(
                factory = viewModelFactory { initializer { TaskEditViewModel(repository, id) } },
            )
            TaskEditScreen(
                viewModel = vm,
                isNew = id == TaskEditViewModel.NEW_TASK,
                onClose = { navController.popBackStack() },
            )
        }
    }
}

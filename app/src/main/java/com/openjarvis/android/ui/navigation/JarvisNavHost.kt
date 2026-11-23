package com.openjarvis.android.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openjarvis.android.ui.chat.ChatScreen
import com.openjarvis.android.ui.memory.MemoryScreen
import com.openjarvis.android.ui.models.ModelsScreen
import com.openjarvis.android.ui.permissions.PermissionsScreen
import com.openjarvis.android.ui.settings.SettingsScreen
import com.openjarvis.android.ui.tasks.TasksScreen

sealed class JarvisRoute(val route: String, val label: String, val icon: ImageVector) {
    data object Chat : JarvisRoute("chat", "Chat", Icons.Default.ChatBubbleOutline)
    data object Tasks : JarvisRoute("tasks", "Tasks", Icons.Default.TaskAlt)
    data object Memory : JarvisRoute("memory", "Memory", Icons.Default.Memory)
    data object Models : JarvisRoute("models", "Models", Icons.Default.ModelTraining)
    data object Settings : JarvisRoute("settings", "Settings", Icons.Default.Settings)
    data object Permissions : JarvisRoute("permissions", "Permissions", Icons.Default.Settings)
}

@Composable
fun JarvisNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val topTabs = listOf(
        JarvisRoute.Chat,
        JarvisRoute.Tasks,
        JarvisRoute.Memory,
        JarvisRoute.Models,
        JarvisRoute.Settings,
    )

    Scaffold(
        bottomBar = {
            if (currentRoute != JarvisRoute.Permissions.route) {
                NavigationBar {
                    topTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = JarvisRoute.Chat.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(JarvisRoute.Chat.route) {
                ChatScreen(
                    onOpenModels = { navController.navigate(JarvisRoute.Models.route) },
                    onOpenSettings = { navController.navigate(JarvisRoute.Settings.route) },
                )
            }
            composable(JarvisRoute.Tasks.route) {
                TasksScreen()
            }
            composable(JarvisRoute.Memory.route) {
                MemoryScreen()
            }
            composable(JarvisRoute.Models.route) {
                ModelsScreen()
            }
            composable(JarvisRoute.Settings.route) {
                SettingsScreen(
                    onOpenPermissions = { navController.navigate(JarvisRoute.Permissions.route) },
                    onOpenModels = { navController.navigate(JarvisRoute.Models.route) },
                )
            }
            composable(JarvisRoute.Permissions.route) {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

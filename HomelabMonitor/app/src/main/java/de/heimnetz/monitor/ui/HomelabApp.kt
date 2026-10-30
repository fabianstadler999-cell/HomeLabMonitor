package de.heimnetz.monitor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun HomelabApp(vm: MainViewModel) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                state = state,
                onRefresh = vm::refresh,
                onOpen = { id -> nav.navigate("detail/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(
                state = state,
                id = id,
                onBack = { nav.popBackStack() },
                onRefresh = vm::refresh,
                onAction = { action -> vm.runAction(id, action) },
                onDismissOutput = vm::clearOutput,
            )
        }
        composable("settings") {
            SettingsScreen(
                config = state.config,
                onSave = { vm.saveConfig(it); nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
    }
}

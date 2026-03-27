package com.ksinfra.clawapk.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ksinfra.clawapk.chat.ui.ChatScreen
import com.ksinfra.clawapk.chat.ui.CloudflareAuthScreen
import com.ksinfra.clawapk.chat.ui.SettingsScreen
import com.ksinfra.clawapk.chat.viewmodel.ChatViewModel
import com.ksinfra.clawapk.chat.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "chat"
    ) {
        composable("chat") {
            val viewModel: ChatViewModel = koinViewModel()
            ChatScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            val viewModel: SettingsViewModel = koinViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCfAuth = { navController.navigate("cf_auth") }
            )
        }
        composable("cf_auth") {
            val viewModel: SettingsViewModel = koinViewModel()
            val serverUrl by viewModel.serverUrl.collectAsState()
            CloudflareAuthScreen(
                serverUrl = serverUrl,
                onCookieObtained = { cookie ->
                    viewModel.onCfCookieObtained(cookie)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

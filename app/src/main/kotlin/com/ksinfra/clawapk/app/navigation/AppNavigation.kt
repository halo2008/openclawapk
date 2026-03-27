package com.ksinfra.clawapk.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ksinfra.clawapk.chat.ui.ChatScreen
import com.ksinfra.clawapk.chat.ui.CloudflareAuthScreen
import com.ksinfra.clawapk.chat.ui.SettingsScreen
import com.ksinfra.clawapk.chat.viewmodel.ChatViewModel
import com.ksinfra.clawapk.chat.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = koinViewModel()

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
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCfAuth = {
                    val url = settingsViewModel.serverUrl.value
                    val encoded = URLEncoder.encode(url, "UTF-8")
                    navController.navigate("cf_auth/$encoded")
                }
            )
        }
        composable(
            route = "cf_auth/{serverUrl}",
            arguments = listOf(navArgument("serverUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("serverUrl") ?: ""
            val serverUrl = URLDecoder.decode(encodedUrl, "UTF-8")
            CloudflareAuthScreen(
                serverUrl = serverUrl,
                onCookieObtained = { cookie ->
                    settingsViewModel.onCfCookieObtained(cookie)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

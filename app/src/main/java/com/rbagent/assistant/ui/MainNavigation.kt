package com.rbagent.assistant.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rbagent.assistant.ui.components.FrostedBottomBar
import com.rbagent.assistant.ui.screens.CodeScreen
import com.rbagent.assistant.ui.screens.FilesScreen
import com.rbagent.assistant.ui.screens.SettingsScreen
import com.rbagent.assistant.ui.screens.WebScreen

object Routes {
    const val HOME = "home"
    const val WEB = "web"
    const val FILES = "files"
    const val CODE = "code"
    const val SETTINGS = "settings"
}

@Composable
fun MainNavigation(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.HOME) {
                MainScreen(viewModel = viewModel)
            }
            composable(Routes.WEB) {
                WebScreen()
            }
            composable(Routes.FILES) {
                FilesScreen()
            }
            composable(Routes.CODE) {
                CodeScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }

        if (!uiState.drawerOpen) {
            FrostedBottomBar(
                selected = routeToTab(currentRoute),
                onSelect = { tab ->
                    val route = tabToRoute(tab)
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private fun tabToRoute(tab: BottomTab): String = when (tab) {
    BottomTab.HOME -> Routes.HOME
    BottomTab.WEB -> Routes.WEB
    BottomTab.FILES -> Routes.FILES
    BottomTab.CODE -> Routes.CODE
    BottomTab.SETTINGS -> Routes.SETTINGS
}

private fun routeToTab(route: String?): BottomTab = when (route) {
    Routes.WEB -> BottomTab.WEB
    Routes.FILES -> BottomTab.FILES
    Routes.CODE -> BottomTab.CODE
    Routes.SETTINGS -> BottomTab.SETTINGS
    else -> BottomTab.HOME
}

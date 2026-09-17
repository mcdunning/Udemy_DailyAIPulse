package com.dailyaipulse.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dailyaipulse.articles.ui.ArticleListScreen
import com.dailyaipulse.sources.ui.SourceListScreen
import kotlinx.serialization.Serializable

@Serializable
object ArticleListRoute

@Serializable
object SourceListRoute

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hasRoute<ArticleListRoute>() == true,
                    onClick = { navController.navigateToTab(ArticleListRoute) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Articles") }
                )
                NavigationBarItem(
                    selected = currentDestination?.hasRoute<SourceListRoute>() == true,
                    onClick = { navController.navigateToTab(SourceListRoute) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Sources") }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = ArticleListRoute,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable<ArticleListRoute> {
                ArticleListScreen()
            }
            composable<SourceListRoute> {
                SourceListScreen()
            }
        }
    }
}

private fun <T : Any> NavController.navigateToTab(route: T) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

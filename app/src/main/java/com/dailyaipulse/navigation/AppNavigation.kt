package com.dailyaipulse.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dailyaipulse.articles.ui.ArticleListScreen
import kotlinx.serialization.Serializable

@Serializable
object ArticleListRoute

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ArticleListRoute) {
        composable<ArticleListRoute> {
            ArticleListScreen()
        }
    }
}

package com.dailyaipulse.articles.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyaipulse.articles.presentation.ArticleListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(viewModel: ArticleListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Articles") }) }
    ) { paddingValues ->
        ArticleListContent(
            uiState = uiState,
            onLoadNextPage = viewModel::loadNextPage,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

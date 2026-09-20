package com.dailyaipulse.articles.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyaipulse.articles.presentation.ArticleListViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(viewModel: ArticleListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Articles") }) }
    ) { paddingValues ->
        ArticleListContent(
            uiState = uiState,
            onLoadNextPage = viewModel::loadNextPage,
            onSummarizeClick = viewModel::summarize,
            onOpenArticleClick = { article ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, article.url.toUri()))
                }.onFailure { e ->
                    Timber.e(e, "No activity available to open ${article.url}")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

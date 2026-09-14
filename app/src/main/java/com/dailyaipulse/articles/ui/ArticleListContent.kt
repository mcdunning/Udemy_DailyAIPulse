package com.dailyaipulse.articles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyaipulse.articles.presentation.ArticleListUiState

@Composable
fun ArticleListContent(
    uiState: ArticleListUiState,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is ArticleListUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("fullScreenLoading")
                )
            }
            is ArticleListUiState.Error -> {
                Text(text = uiState.message, modifier = Modifier.align(Alignment.Center))
            }
            is ArticleListUiState.Success -> {
                if (uiState.articles.isEmpty()) {
                    Text(text = "No articles found", modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn {
                        itemsIndexed(uiState.articles) { index, article ->
                            ArticleListItem(article)
                            if (index == uiState.articles.lastIndex && !uiState.isLoadingMore) {
                                LaunchedEffect(Unit) { onLoadNextPage() }
                            }
                        }
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.testTag("paginationLoading"))
                                }
                            }
                        }
                        if (uiState.paginationError != null) {
                            item {
                                Text(
                                    text = "Failed to load more — tap to retry",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onLoadNextPage() }
                                        .padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

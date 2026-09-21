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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dailyaipulse.articles.presentation.Article
import com.dailyaipulse.articles.presentation.ArticleListUiState
import com.dailyaipulse.core.GeneratedPreview
import com.dailyaipulse.summary.presentation.SummaryUiState

@Composable
fun ArticleListContent(
    uiState: ArticleListUiState,
    onLoadNextPage: () -> Unit,
    onSummarizeClick: (Article) -> Unit,
    onOpenArticleClick: (Article) -> Unit,
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
                Text(
                    text = uiState.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
            is ArticleListUiState.Success -> {
                if (uiState.articles.isEmpty()) {
                    Text(
                        text = "No articles found",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(uiState.articles) { index, article ->
                            ArticleListItem(
                                article = article,
                                summaryState = uiState.summaries[article.url] ?: SummaryUiState.Idle,
                                onSummarize = { onSummarizeClick(article) },
                                onOpenArticle = { onOpenArticleClick(article) }
                            )
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

private val previewArticle = Article(
    title = "Sample Headline About Technology",
    description = "A short sample description of the article content, for preview purposes.",
    imageUrl = null,
    date = "Sep 13, 2026",
    content = "Sample truncated article content for preview purposes...",
    url = "https://example.com/sample-article"
)

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun ArticleListContentLoadingPreview() {
    ArticleListContent(uiState = ArticleListUiState.Loading, onLoadNextPage = {}, onSummarizeClick = {}, onOpenArticleClick = {})
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun ArticleListContentErrorPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Error("Something went wrong.\nPlease try again."),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun ArticleListContentEmptyPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(articles = emptyList()),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun ArticleListContentSuccessPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(
            articles = listOf(
                previewArticle,
                previewArticle.copy(
                    title = "Second Sample Headline",
                    date = "3h ago",
                    url = "https://example.com/sample-article-2"
                )
            )
        ),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun ArticleListContentLoadingMorePreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(articles = listOf(previewArticle), isLoadingMore = true),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun ArticleListContentPaginationErrorPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(
            articles = listOf(previewArticle),
            paginationError = "Failed to load more"
        ),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
@GeneratedPreview
private fun ArticleListContentSummarizedPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(
            articles = listOf(previewArticle),
            summaries = mapOf(
                previewArticle.url to SummaryUiState.Success(
                    "A concise, two-sentence AI-generated summary of the article, shown here for preview purposes."
                )
            )
        ),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

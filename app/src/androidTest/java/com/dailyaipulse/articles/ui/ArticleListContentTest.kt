package com.dailyaipulse.articles.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dailyaipulse.articles.presentation.Article
import com.dailyaipulse.articles.presentation.ArticleListUiState
import org.junit.Rule
import org.junit.Test

class ArticleListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val article = Article(
        title = "Some Headline",
        description = "Some description",
        imageUrl = null,
        date = "Sep 13, 2026"
    )

    @Test
    fun showsFullScreenLoadingIndicator_whenStateIsLoading() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Loading, onLoadNextPage = {})
        }

        composeTestRule.onNodeWithTag("fullScreenLoading").assertExists()
    }

    @Test
    fun showsErrorMessage_whenStateIsError() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Error("Something went wrong"), onLoadNextPage = {})
        }

        composeTestRule.onNodeWithText("Something went wrong").assertExists()
    }

    @Test
    fun showsNoArticlesFound_whenSuccessWithEmptyList() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Success(articles = emptyList()), onLoadNextPage = {})
        }

        composeTestRule.onNodeWithText("No articles found").assertExists()
    }

    @Test
    fun showsArticles_whenSuccessWithArticles() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Success(articles = listOf(article)), onLoadNextPage = {})
        }

        composeTestRule.onNodeWithText("Some Headline").assertExists()
    }

    @Test
    fun showsPaginationLoadingIndicator_whenIsLoadingMore() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article), isLoadingMore = true),
                onLoadNextPage = {}
            )
        }

        composeTestRule.onNodeWithTag("paginationLoading").assertExists()
    }

    @Test
    fun tappingRetryRow_invokesOnLoadNextPage_whenPaginationErrorSet() {
        var retried = false
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article), paginationError = "failed"),
                onLoadNextPage = { retried = true }
            )
        }

        composeTestRule.onNodeWithText("Failed to load more — tap to retry").performClick()

        assert(retried)
    }
}

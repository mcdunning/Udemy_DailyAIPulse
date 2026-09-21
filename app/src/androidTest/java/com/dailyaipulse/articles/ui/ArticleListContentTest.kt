package com.dailyaipulse.articles.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dailyaipulse.articles.presentation.Article
import com.dailyaipulse.articles.presentation.ArticleListUiState
import com.dailyaipulse.summary.presentation.SummaryUiState
import com.dailyaipulse.ui.theme.DailyAIPulseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ArticleListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val article = Article(
        title = "Some Headline",
        description = "Some description",
        imageUrl = null,
        date = "Sep 13, 2026",
        content = "Some truncated content",
        url = "https://example.com/some-headline"
    )

    @Test
    fun showsFullScreenLoadingIndicator_whenStateIsLoading() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(uiState = ArticleListUiState.Loading, onLoadNextPage = {}, onSummarizeClick = {}, onOpenArticleClick = {})
            }
        }

        composeTestRule.onNodeWithTag("fullScreenLoading").assertExists()
    }

    @Test
    fun showsErrorMessage_whenStateIsError() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Error("Something went wrong"),
                    onLoadNextPage = {},
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Something went wrong").assertExists()
    }

    @Test
    fun showsNoArticlesFound_whenSuccessWithEmptyList() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(articles = emptyList()),
                    onLoadNextPage = {},
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No articles found").assertExists()
    }

    @Test
    fun showsArticles_whenSuccessWithArticles() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(articles = listOf(article)),
                    onLoadNextPage = {},
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Some Headline").assertExists()
    }

    @Test
    fun showsPaginationLoadingIndicator_whenIsLoadingMore() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(articles = listOf(article), isLoadingMore = true),
                    onLoadNextPage = {},
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("paginationLoading").assertExists()
    }

    @Test
    fun tappingRetryRow_invokesOnLoadNextPage_whenPaginationErrorSet() {
        var retried = false
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(articles = listOf(article), paginationError = "failed"),
                    onLoadNextPage = { retried = true },
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load more — tap to retry").performClick()

        assert(retried)
    }

    @Test
    fun showsSummarizeButton_whenSummaryStateIsIdle() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(articles = listOf(article)),
                    onLoadNextPage = {},
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Summarize").assertExists()
    }

    @Test
    fun showsSummaryLoadingIndicator_whenSummaryStateIsLoading() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(
                        articles = listOf(article),
                        summaries = mapOf(article.url to SummaryUiState.Loading)
                    ),
                    onLoadNextPage = {},
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("summaryLoading").assertExists()
    }

    @Test
    fun showsSummaryText_whenSummaryStateIsSuccess() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(
                        articles = listOf(article),
                        summaries = mapOf(article.url to SummaryUiState.Success("A concise AI-generated summary."))
                    ),
                    onLoadNextPage = {},
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("A concise AI-generated summary.").assertExists()
    }

    @Test
    fun showsRetryButtonAndErrorMessage_whenSummaryStateIsError() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(
                        articles = listOf(article),
                        summaries = mapOf(article.url to SummaryUiState.Error("Something went wrong.\nPlease try again."))
                    ),
                    onLoadNextPage = {},
                    onSummarizeClick = {},
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Something went wrong.\nPlease try again.").assertExists()
        composeTestRule.onNodeWithText("Retry summarize").assertExists()
    }

    @Test
    fun tappingSummarizeButton_invokesOnSummarizeClick_withTappedArticle() {
        var summarized: Article? = null
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(articles = listOf(article)),
                    onLoadNextPage = {},
                    onSummarizeClick = { summarized = it },
                    onOpenArticleClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Summarize").performClick()

        assertEquals(article, summarized)
    }

    @Test
    fun tappingCard_invokesOnOpenArticleClick_withoutInvokingOnSummarizeClick() {
        var opened: Article? = null
        var summarized: Article? = null
        composeTestRule.setContent {
            DailyAIPulseTheme {
                ArticleListContent(
                    uiState = ArticleListUiState.Success(articles = listOf(article)),
                    onLoadNextPage = {},
                    onSummarizeClick = { summarized = it },
                    onOpenArticleClick = { opened = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Some Headline").performClick()

        assertEquals(article, opened)
        assertNull(summarized)
    }
}

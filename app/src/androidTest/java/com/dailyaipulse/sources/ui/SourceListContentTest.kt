package com.dailyaipulse.sources.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.dailyaipulse.sources.presentation.Source
import com.dailyaipulse.sources.presentation.SourceListUiState
import com.dailyaipulse.ui.theme.DailyAIPulseTheme
import org.junit.Rule
import org.junit.Test

class SourceListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val source = Source(
        name = "TechCrunch",
        description = "Startup and tech news"
    )

    @Test
    fun showsFullScreenLoadingIndicator_whenStateIsLoading() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                SourceListContent(uiState = SourceListUiState.Loading)
            }
        }

        composeTestRule.onNodeWithTag("fullScreenLoading").assertExists()
    }

    @Test
    fun showsErrorMessage_whenStateIsError() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                SourceListContent(uiState = SourceListUiState.Error("Something went wrong"))
            }
        }

        composeTestRule.onNodeWithText("Something went wrong").assertExists()
    }

    @Test
    fun showsNoSourcesFound_whenSuccessWithEmptyList() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                SourceListContent(uiState = SourceListUiState.Success(sources = emptyList()))
            }
        }

        composeTestRule.onNodeWithText("No sources found").assertExists()
    }

    @Test
    fun showsSources_whenSuccessWithSources() {
        composeTestRule.setContent {
            DailyAIPulseTheme {
                SourceListContent(uiState = SourceListUiState.Success(sources = listOf(source)))
            }
        }

        composeTestRule.onNodeWithText("TechCrunch").assertExists()
        composeTestRule.onNodeWithText("Startup and tech news").assertExists()
    }
}

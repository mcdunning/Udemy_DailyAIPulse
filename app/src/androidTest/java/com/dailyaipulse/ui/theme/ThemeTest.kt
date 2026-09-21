package com.dailyaipulse.ui.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersContent_withDynamicColorDisabled_lightTheme() {
        composeTestRule.setContent {
            DailyAIPulseTheme(darkTheme = false, dynamicColor = false) {
                Text("Themed content")
            }
        }

        composeTestRule.onNodeWithText("Themed content").assertExists()
    }

    @Test
    fun rendersContent_withDynamicColorDisabled_darkTheme() {
        composeTestRule.setContent {
            DailyAIPulseTheme(darkTheme = true, dynamicColor = false) {
                Text("Themed content")
            }
        }

        composeTestRule.onNodeWithText("Themed content").assertExists()
    }

    @Test
    fun rendersContent_withDynamicColorEnabled() {
        composeTestRule.setContent {
            DailyAIPulseTheme(darkTheme = false, dynamicColor = true) {
                Text("Themed content")
            }
        }

        composeTestRule.onNodeWithText("Themed content").assertExists()
    }
}

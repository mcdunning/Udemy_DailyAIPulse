package com.dailyaipulse

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// One shared Activity launch drives all of these: MainActivity's onCreate, AppNavigation's
// NavHost/bottom-bar wiring, and both ArticleListScreen/SourceListScreen composing their real
// (Hilt-injected) ViewModels. Splitting into separate test classes would just repeat the same
// launch. Assertions only touch each screen's static TopAppBar title, never network-loaded
// content, since the real repositories fire real (unmocked) network calls on ViewModel init.
@HiltAndroidTest
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun launchesOnArticlesTab_byDefault() {
        composeTestRule.onNodeWithTag("screenTitle").assertTextEquals("Articles")
    }

    @Test
    fun tappingSourcesTab_switchesToSourceListScreen() {
        composeTestRule.onNodeWithTag("tab_sources").performClick()

        composeTestRule.onNodeWithTag("screenTitle").assertTextEquals("Sources")
    }

    @Test
    fun tappingBackToArticlesTab_afterSwitchingToSources_restoresArticleListScreen() {
        composeTestRule.onNodeWithTag("tab_sources").performClick()
        composeTestRule.onNodeWithTag("tab_articles").performClick()

        composeTestRule.onNodeWithTag("screenTitle").assertTextEquals("Articles")
    }
}

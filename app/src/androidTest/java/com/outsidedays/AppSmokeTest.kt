package com.outsidedays

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetDisposableTestData() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OutsideApp
        app.repository.deleteAll()
        app.repository.updatePreferences { it.copy(introSeen = true, reducedMotion = true) }
    }
    private fun awaitText(text: String) {
        compose.waitUntil(20000) { compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun welcome() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("Make myself at home").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Good things grow outside.").fetchSemanticsNodes().isNotEmpty() }
        if(compose.onAllNodesWithText("Make myself at home").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Make myself at home").performClick()
    }
    @Test fun allFiveGamesOpenPlayAndPause() {
        welcome()
        compose.onNodeWithText("Play",useUnmergedTree=true).performClick()
        listOf("Leaf Match", "Cloud Slide", "Firefly Garden", "Pocket Sudoku", "Cozy Merge").forEach { name ->
            compose.onNodeWithTag("open-$name").performScrollTo().performClick()
            awaitText("Play / resume")
            compose.onNodeWithText("Play / resume").performScrollTo().performClick()
            compose.onNodeWithText("Pause game").performScrollTo().performClick()
            compose.onNodeWithText("Resting here. Your progress is saved.").assertExists()
            compose.onNodeWithText("‹ All games").performScrollTo().performClick()
        }
    }
    @Test fun startPauseNavigateResumeAndFinish() {
        welcome()
        compose.onNodeWithText("I’m going outside").performScrollTo().performClick()
        awaitText("Pause")
        compose.onNodeWithText("Pause",useUnmergedTree=true).performScrollTo().performClick()
        awaitText("OUTSIDE · PAUSED")
        compose.onNodeWithText("OUTSIDE · PAUSED").assertIsDisplayed()
        compose.onNodeWithText("Play",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Pocket of play").assertIsDisplayed()
        compose.onNodeWithText("Today",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Resume").performScrollTo().performClick()
        awaitText("OUTSIDE · RUNNING")
        compose.onNodeWithText("OUTSIDE · RUNNING").assertExists()
        compose.activityRule.scenario.recreate()
        awaitText("I’m back home")
        compose.onNodeWithText("I’m back home").performScrollTo().performClick()
        awaitText("Save memory")
        compose.onNodeWithText("Save memory").performScrollTo().performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithText("Save memory").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Journal",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Your sky journal").assertIsDisplayed()
    }
    @Test fun gameSurvivesActivityRecreation() {
        welcome()
        compose.onNodeWithText("Play",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("open-Leaf Match").performScrollTo().performClick()
        awaitText("Play / resume")
        compose.onNodeWithText("Play / resume").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Face-down card 1").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        awaitText("Leaf Match")
        compose.onNodeWithText("Leaf Match").assertExists()
        compose.onNodeWithText("Play / resume").assertExists()
    }
}

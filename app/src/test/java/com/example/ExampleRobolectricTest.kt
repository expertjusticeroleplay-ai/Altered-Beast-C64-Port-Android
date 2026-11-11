package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h480dp-land")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenDisplaysC64CanvasAndFloatingSvgControls() {
        composeTestRule.onNodeWithTag("c64_screen_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dpad_control").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fire_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("f1_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("f7_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("restore_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fps_cap_badge").assertIsDisplayed()
    }
}

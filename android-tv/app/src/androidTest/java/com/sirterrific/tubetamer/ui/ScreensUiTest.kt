package com.sirterrific.tubetamer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sirterrific.tubetamer.R
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.api.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensUiTest {
    @get:Rule val rule = createComposeRule()

    /** TV buttons answer the remote (D-pad center) and the semantic click, not simulated touch. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.press() =
        performSemanticsAction(SemanticsActions.OnClick)

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun pinPadSubmitsTypedDigits() {
        var submitted: String? = null
        rule.setContent {
            TubeTamerTheme {
                PinScreen(Screen.Pin(Profile("default", "Alice", hasPin = true))) { submitted = it }
            }
        }
        rule.onNodeWithText(str(R.string.pin_title, "Alice")).assertIsDisplayed()
        listOf("1", "2", "3", "4").forEach { rule.onNodeWithText(it).press() }
        rule.onNodeWithText(str(R.string.ok)).press()
        assertEquals("1234", submitted)
    }

    @Test fun pinErrorIsShown() {
        rule.setContent {
            TubeTamerTheme {
                PinScreen(Screen.Pin(Profile("default", "Alice", hasPin = true), error = UiError.WRONG_PIN)) {}
            }
        }
        rule.onNodeWithText(str(R.string.err_wrong_pin)).assertIsDisplayed()
    }

    @Test fun serverSetupShowsError() {
        rule.setContent {
            TubeTamerTheme {
                ServerSetupScreen(Screen.ServerSetup("10.0.0.9", UiError.UNREACHABLE)) {}
            }
        }
        rule.onNodeWithText(str(R.string.err_unreachable)).assertIsDisplayed()
        rule.onNodeWithText("10.0.0.9").assertIsDisplayed()
    }

    @Test fun videoCardShowsTitleDurationAndBadge() {
        rule.setContent {
            TubeTamerTheme {
                VideoCardView(
                    VideoCard("abc12345678", title = "Cats", channelName = "Cat World", duration = 245),
                    baseUrl = "",
                    onClick = {},
                    badge = "Approved",
                )
            }
        }
        rule.onNodeWithText("Cats").assertIsDisplayed()
        rule.onNodeWithText("Cat World").assertIsDisplayed()
        rule.onNodeWithText("4:05").assertIsDisplayed()
        rule.onNodeWithText("Approved").assertIsDisplayed()
    }
}

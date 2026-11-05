package com.sirterrific.tubetamer.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.api.VideoCard
import com.sirterrific.tubetamer.core.R as CoreR
import com.sirterrific.tubetamer.ui.Screen
import com.sirterrific.tubetamer.ui.UiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileScreensUiTest {
    @get:Rule val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun pinFieldSubmitsTypedDigits() {
        var submitted: String? = null
        rule.setContent {
            MobileTheme { PinScreen(Screen.Pin(Profile("default", "Alice", hasPin = true))) { submitted = it } }
        }
        rule.onNodeWithText(str(CoreR.string.pin_title, "Alice")).assertIsDisplayed()
        rule.onNodeWithText(str(CoreR.string.ok)).performClick() // empty PIN: nothing sent
        assertEquals(null, submitted)
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("1234")
        rule.onNodeWithText(str(CoreR.string.ok)).performClick()
        assertEquals("1234", submitted)
    }

    @Test fun pinErrorIsShown() {
        rule.setContent {
            MobileTheme {
                PinScreen(Screen.Pin(Profile("default", "Alice", hasPin = true), error = UiError.WRONG_PIN)) {}
            }
        }
        rule.onNodeWithText(str(CoreR.string.err_wrong_pin)).assertIsDisplayed()
    }

    @Test fun serverSetupShowsErrorAndAddress() {
        var connected: String? = null
        rule.setContent {
            MobileTheme { ServerSetupScreen(Screen.ServerSetup("10.0.0.9", UiError.UNREACHABLE)) { connected = it } }
        }
        rule.onNodeWithText(str(CoreR.string.err_unreachable)).assertIsDisplayed()
        rule.onNodeWithText("10.0.0.9").assertIsDisplayed()
        rule.onNodeWithText(str(CoreR.string.setup_connect)).performClick()
        assertEquals("10.0.0.9", connected)
    }

    @Test fun videoCardShowsTitleDurationAndBadge() {
        rule.setContent {
            MobileTheme {
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

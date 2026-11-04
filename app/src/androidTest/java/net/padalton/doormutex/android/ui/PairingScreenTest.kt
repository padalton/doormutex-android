package net.padalton.doormutex.android.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.data.PairLink
import net.padalton.doormutex.android.ui.pairing.PairingScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Before
    fun grantCamera() {
        // The permission dialog would cover the screen under test.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.CAMERA)
    }

    @Test
    fun checkNeedsAValidAddressAndAToken() {
        compose.setContent { PairingScreen(onPaired = {}) }
        compose.onNodeWithTag("pair-check").assertIsNotEnabled()
        compose.onNodeWithTag("pair-url").performTextInput("not a hub")
        compose.onNodeWithText(string(R.string.pair_invalid_url)).assertIsDisplayed()
        compose.onNodeWithTag("pair-check").assertIsNotEnabled()
    }

    @Test
    fun plainHttpOnAPublicAddressIsRefused() {
        compose.setContent { PairingScreen(onPaired = {}) }
        compose.onNodeWithTag("pair-url").performTextInput("http://hub.example")
        compose.onNodeWithTag("pair-token").performTextInput("dm_x")
        compose.onNodeWithText(string(R.string.pair_cleartext_public)).assertIsDisplayed()
        compose.onNodeWithTag("pair-check").assertIsNotEnabled()
    }

    @Test
    fun plainHttpAtHomeNeedsTheWarningAccepted() {
        compose.setContent { PairingScreen(onPaired = {}) }
        compose.onNodeWithTag("pair-url").performTextInput("http://192.168.1.20:8080")
        compose.onNodeWithTag("pair-token").performTextInput("dm_x")
        compose.onNodeWithTag("pair-check").assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.pair_cleartext_accept)).performClick()
        compose.onNodeWithTag("pair-check").assertIsEnabled()
    }

    @Test
    fun anHttpPairingLinkFillsInAndWaitsForTheWarning() {
        compose.setContent { PairingScreen(onPaired = {}, link = PairLink("http://192.168.1.20:8080", "dm_x", "couch")) }
        compose.onNodeWithText("http://192.168.1.20:8080").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.pair_cleartext_warning)).assertIsDisplayed()
        compose.onNodeWithTag("pair-check").assertIsNotEnabled()
    }

    @Test
    fun scanButtonOpensTheScanner() {
        compose.setContent { PairingScreen(onPaired = {}) }
        compose.onNodeWithTag("pair-scan").performClick()
        compose.onNodeWithText(string(R.string.scan_hint)).assertIsDisplayed()
    }
}

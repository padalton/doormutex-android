package net.padalton.doormutex.android.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.padalton.doormutex.android.data.Light
import net.padalton.doormutex.android.data.ProfileInfo
import net.padalton.doormutex.android.data.Reason
import net.padalton.doormutex.android.data.State
import net.padalton.doormutex.android.data.Status
import net.padalton.doormutex.android.ui.overview.ProfileCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
class ProfileCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val profile =
        ProfileInfo(
            "office",
            "Office",
            "office",
            State(
                status = Status.DND,
                light = Light.RED,
                reason = Reason.CALENDAR,
                since = OffsetDateTime.now().minusMinutes(5),
                until = OffsetDateTime.now().plusHours(1),
                updated = OffsetDateTime.now(),
            ),
        )

    @Test
    fun talkBackReadsNameStatusAndUntil() {
        compose.setContent { ProfileCard(profile, stale = false) }
        // "Office: Do not disturb, until 18:00" – one announcement for the whole card.
        compose.onNode(hasContentDescription("Office: Do not disturb, until", substring = true)).assertExists()
    }

    @Test
    fun aClickOpensTheProfile() {
        var opened = false
        compose.setContent { ProfileCard(profile, stale = false) { opened = true } }
        compose.onNodeWithTag("profile-office").performClick()
        assertTrue(opened)
    }
}

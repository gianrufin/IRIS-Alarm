package com.iris.alarm.ui.challenge

import android.Manifest
import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ringing flow's UI contract: the challenge surface comes up, keeps the
 * screen awake, and cannot be dismissed with the back gesture.
 *
 * Whether the window genuinely draws over a secured keyguard is an OEM
 * behaviour that no instrumentation assert can stand in for — it still needs a
 * locked physical device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AlarmChallengeActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun theChallengeSurfaceRendersAndHoldsTheScreenOn() {
        ActivityScenario.launch<AlarmChallengeActivity>(
            AlarmChallengeActivity.intent(context, ALARM_ID),
        ).use { scenario ->
            // The clock is in the header for every challenge, so it is the one
            // assertion that does not depend on which detector resolved.
            composeRule
                .onNodeWithText(LocalTime.now().format(TIME_FORMAT))
                .assertExists()

            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertTrue(
                    "The ringing screen must not be allowed to time out",
                    flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
                )
            }
        }
    }

    @Test
    fun backDoesNotDismissTheAlarm() {
        ActivityScenario.launch<AlarmChallengeActivity>(
            AlarmChallengeActivity.intent(context, ALARM_ID),
        ).use { scenario ->
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            // Backing out would leave the alarm ringing with no route back to the
            // challenge, so the Activity must survive it.
            assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    private companion object {
        const val ALARM_ID = 1L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

package com.srini.trafficfine.screenshots

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.srini.trafficfine.MainActivity
import com.srini.trafficfine.ui.dashboard.DASHBOARD_LIST_TAG
import com.srini.trafficfine.ui.history.HISTORY_ROW_TAG
import com.srini.trafficfine.ui.issue.PLATE_FIELD_TAG
import com.srini.trafficfine.ui.login.LoginTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through the main screens in demo mode and saves full-device screenshots to
 * `files/screenshots/` inside the app sandbox, where scripts/capture-screenshots.sh pulls them
 * from. Pass `-e variant dark` to suffix the file names (the script switches the system theme).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        .around(composeRule)

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val variant: String = InstrumentationRegistry.getArguments().getString("variant", "light")

    @Test
    fun tourOfMainScreens() {
        composeRule.waitUntilAtLeastOneExists(hasTestTag(LoginTestTags.DEMO_ADMIN), TIMEOUT)
        capture("login")

        composeRule.onNodeWithTag(LoginTestTags.DEMO_ADMIN).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Admin console", substring = true), TIMEOUT)
        composeRule.waitUntilAtLeastOneExists(hasText("Recent challans"), TIMEOUT)
        capture("dashboard")

        composeRule.onNodeWithText("Issue challan").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag(PLATE_FIELD_TAG), TIMEOUT)
        composeRule.onNodeWithTag(PLATE_FIELD_TAG).performTextInput("TN09AB1234")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithText("Riding without helmet", substring = true).performClick()
        composeRule.onNodeWithText("Driving without seat belt", substring = true).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Adyar", substring = true), TIMEOUT)
        composeRule.waitUntilAtLeastOneExists(hasText("Total fine"), TIMEOUT)
        capture("issue")
        Espresso.pressBack()

        composeRule.waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_LIST_TAG), TIMEOUT)
        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(hasText("History"))
        composeRule.onNodeWithText("History").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HISTORY_ROW_TAG), TIMEOUT)
        capture("history")

        composeRule.onAllNodesWithTag(HISTORY_ROW_TAG).onFirst().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Challan details"), TIMEOUT)
        composeRule.waitUntilAtLeastOneExists(hasText("Total"), TIMEOUT)
        capture("detail")
        Espresso.pressBack()
        Espresso.pressBack()

        composeRule.waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_LIST_TAG), TIMEOUT)
        val adminButton = hasText("Admin console", substring = true)
        composeRule.onNodeWithTag(DASHBOARD_LIST_TAG).performScrollToNode(adminButton)
        composeRule.onNode(adminButton).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Analytics"), TIMEOUT)
        composeRule.waitUntilAtLeastOneExists(hasContentDescription("Daily fines chart", substring = true), TIMEOUT)
        capture("admin")
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        SystemClock.sleep(SETTLE_MS) // let ripples, transitions and the cursor settle
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot failed for $name")
        val dir = File(instrumentation.targetContext.filesDir, "screenshots").apply { mkdirs() }
        File(dir, "$name-$variant.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val SETTLE_MS = 800L
    }
}

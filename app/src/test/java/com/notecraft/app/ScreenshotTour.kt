package com.notecraft.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Boots the real app headlessly (Robolectric + native graphics), walks through the main
 * screens and saves screenshots to app/build/screenshots for visual review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
abstract class TourBase {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    abstract val prefix: String

    protected fun settle(ms: Long = 1200) {
        repeat((ms / 100).toInt()) {
            compose.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
            Thread.sleep(15)
        }
        compose.waitForIdle()
    }

    protected fun shot(name: String) {
        settle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$prefix-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    protected fun waitText(text: String) = compose.waitUntil(15_000) {
        ShadowLooper.idleMainLooper()
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    protected fun back() { compose.activity.onBackPressedDispatcher.onBackPressed(); settle(900) }
}

class LightTour : TourBase() {
    override val prefix = "light"

    @Test fun tour() {
        compose.mainClock.autoAdvance = false
        waitText("Welcome to NoteCraft")
        shot("01-home")

        compose.onNodeWithContentDescription("Toggle layout").performClick()
        shot("02-home-list")
        compose.onNodeWithContentDescription("Toggle layout").performClick()

        compose.onNodeWithText("Welcome to NoteCraft").performClick()
        waitText("Organise without folders")
        shot("03-editor")
        compose.onNodeWithContentDescription("Reading mode").performClick()
        shot("04-reading")
        back()
        back()

        compose.onNodeWithContentDescription("Lists").performClick()
        waitText("Weekend groceries")
        shot("06-lists")
        compose.onNodeWithText("Weekend groceries").performClick()
        waitText("Sourdough bread")
        shot("07-list-editor")
        back()

        compose.onNodeWithContentDescription("Reminders").performClick()
        settle()
        shot("08-reminders-empty")
        compose.onNode(hasSetTextAction()).performTextInput("Call mom tomorrow at 6pm")
        shot("09-reminder-quickadd")
        compose.onNodeWithContentDescription("Add").performClick()
        settle()
        shot("10-reminders")

        compose.onNodeWithContentDescription("Notes").performClick()
        settle()
        compose.onNode(hasText("Search notes", substring = true)).performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("ideas")
        shot("11-search")
        back()

        compose.onAllNodes(hasContentDescription("Settings")).onFirst().performClick()
        shot("12-settings")
        back()

        compose.onNodeWithContentDescription("New").performClick()
        shot("13-new-note")
    }
}

@Config(qualifiers = "w400dp-h880dp-xxhdpi-night")
class DarkTour : TourBase() {
    override val prefix = "dark"

    @Test fun tour() {
        compose.mainClock.autoAdvance = false
        waitText("Welcome to NoteCraft")
        shot("01-home")
        compose.onNodeWithText("Welcome to NoteCraft").performClick()
        waitText("Organise without folders")
        shot("02-editor")
        back()
        compose.onNodeWithContentDescription("Lists").performClick()
        waitText("Weekend groceries")
        compose.onNodeWithText("Weekend groceries").performClick()
        waitText("Sourdough bread")
        shot("03-list-editor")
    }
}

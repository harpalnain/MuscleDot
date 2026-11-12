package com.example.fit.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.fit.ui.components.ChartGallery
import org.junit.Rule
import org.junit.Test

/** Renders every chart with the mockup's data, light and dark, and saves pictures of them to look at. */
class ChartGalleryTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun light() {
        rule.setContent { ChartGallery(darkTheme = false) }
        rule.saveScreenshot("charts-light")
    }

    @Test
    fun dark() {
        rule.setContent { ChartGallery(darkTheme = true) }
        rule.saveScreenshot("charts-dark")
    }
}

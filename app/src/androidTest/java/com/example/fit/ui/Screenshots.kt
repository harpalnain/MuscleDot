package com.example.fit.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Saves what is on screen as `<name>.png` in the app's external files folder
 * (`/sdcard/Android/data/app.overload.tracker/files/screenshots`), for a person to
 * look at. Run the test with `adb shell am instrument` so the app stays installed
 * and the files can be pulled.
 */
fun ComposeTestRule.saveScreenshot(name: String) {
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("screenshots")!!
    File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

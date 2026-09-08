package com.meerkly.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.meerkly.android.ui.theme.Bone
import com.meerkly.android.ui.theme.MeerklyTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

/**
 * Renders screens to PNG on the JVM so the design can be reviewed without a
 * device. Writes to build/design-renders/ and asserts nothing — the value is in
 * looking at the output, and it must never fail CI for a visual reason.
 *
 * Deliberately does NOT use createComposeRule: its finders and captureToImage
 * both sync on an idle clock, and the pulsing connection dot is an infinite
 * transition that never goes idle. Drawing the decor view straight to a Canvas
 * sidesteps the idling policy entirely.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DesignRenderTest {

    private fun render(
        name: String,
        widthDp: Int = 411,
        heightDp: Int = 891,
        content: @Composable () -> Unit,
    ) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        activity.setContent {
            MeerklyTheme {
                Box(Modifier.fillMaxSize().background(Bone)) { content() }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()

        val density = activity.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        val view: View = activity.window.decorView
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val out = File("build/design-renders").apply { mkdirs() }
        val file = File(out, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("RENDERED ${file.absolutePath} ${bitmap.width}x${bitmap.height}")
        controller.pause().stop().destroy()
    }

    @Test
    fun `brand art renders`() {
        render("mascot", widthDp = 200, heightDp = 240) { MeerklyMascot(Modifier.fillMaxSize()) }
    }

    @Test
    fun `navigation bar shows every icon, not just the selected one`() {
        render("navbar", widthDp = 411, heightDp = 96) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
            ) { MeerklyNavigationBarPreview() }
        }
    }
}

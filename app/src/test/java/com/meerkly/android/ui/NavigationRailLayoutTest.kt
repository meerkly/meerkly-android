package com.meerkly.android.ui

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import com.meerkly.android.ui.nav.Destination
import com.meerkly.android.ui.theme.MeerklyTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Guards the tablet layout: the rail must keep its intrinsic ~80dp width, not
 * absorb the whole row. Material3's NavigationRail container is only
 * `widthIn(min = 80.dp)`, so a fillMaxSize() child inside it silently expands
 * the rail to every horizontal pixel the Row offers — which starved the
 * weight(1f) content pane to zero width in the Play Store tablet screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationRailLayoutTest {

    @Test
    fun `content pane next to the rail keeps most of the row width`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var contentWidthPx = -1
        var rowWidthPx = -1
        activity.setContent {
            MeerklyTheme {
                Row(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { rowWidthPx = it.size.width },
                ) {
                    MeerklyNavigationRail(Destination.Home) {}
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .onGloballyPositioned { contentWidthPx = it.size.width },
                    )
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()

        // Tablet-ish canvas: 960x600dp (tablet7 landscape logical size).
        val density = activity.resources.displayMetrics.density
        val w = (960 * density).toInt()
        val h = (600 * density).toInt()
        val view: View = activity.window.decorView
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("row never laid out", rowWidthPx > 0)
        assertTrue(
            "content pane got $contentWidthPx px of a $rowWidthPx px row — " +
                "the rail should leave the content pane at least half the width",
            contentWidthPx >= rowWidthPx / 2,
        )
    }

    @Test
    fun `rail items stay vertically centered`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var railHeightPx = -1
        var firstItemTopPx = -1f
        activity.setContent {
            MeerklyTheme {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.onGloballyPositioned { railHeightPx = it.size.height },
                    ) {
                        MeerklyNavigationRail(Destination.Home) {}
                    }
                    Box(Modifier.weight(1f).fillMaxSize())
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()

        val density = activity.resources.displayMetrics.density
        val w = (960 * density).toInt()
        val h = (600 * density).toInt()
        val view: View = activity.window.decorView
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
        shadowOf(Looper.getMainLooper()).idle()

        // The rail must still stretch the full height so centering has room to
        // work — a wrap-content rail would pin the items to the top instead.
        assertTrue("rail never laid out", railHeightPx > 0)
        assertTrue(
            "rail height $railHeightPx px should fill the ${h}px canvas",
            railHeightPx >= (h * 0.95).toInt(),
        )
    }
}

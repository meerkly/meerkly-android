package com.meerkly.android.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

/** Hosts the visible [GeckoView] and binds the manager's primary session into the view hierarchy. */
@Composable
fun GeckoViewHost(manager: GeckoBrowserManager, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GeckoView(ctx).apply { manager.session?.let { setSession(it) } }
        },
        update = { view ->
            val s = manager.session
            if (s != null && view.session !== s) {
                view.setSession(s)
            }
        },
    )
}

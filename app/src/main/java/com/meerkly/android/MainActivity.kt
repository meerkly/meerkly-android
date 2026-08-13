package com.meerkly.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meerkly.android.data.DemoData
import com.meerkly.android.diagnostics.DiagnosticsExporter
import com.meerkly.android.ui.MainViewModel
import com.meerkly.android.ui.RootScreen
import com.meerkly.android.ui.nav.Destination
import com.meerkly.android.ui.theme.MeerklyTheme
import com.meerkly.android.worker.WorkerServiceLauncher

class MainActivity : ComponentActivity() {

    private companion object {
        const val EXTRA_SCREEN = "meerkly.screen"
        const val EXTRA_DEMO = "meerkly.demo"
    }

    override fun onStart() {
        super.onStart()
        // App is foregrounded — the always-legal moment to (re)raise the worker
        // service. No-ops unless enabled + paired (WorkerServiceLauncher).
        WorkerServiceLauncher.startIfEligible(this, (application as MeerklyApp).graph)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug-only, like EXTRA_SCREEN below: seeds placeholder crawls so
        // scripts/screenshots.sh never shoots an empty activity feed (the ring
        // is in-memory and starts empty on every cold start).
        if (BuildConfig.DEBUG && intent?.hasExtra(EXTRA_DEMO) == true) {
            DemoData.seed((application as MeerklyApp).graph.recentRepo)
        }
        setContent {
            MeerklyTheme {
                val vm: MainViewModel = viewModel()
                // No Scaffold: it would inset the whole tree uniformly, leaving a
                // bottom navigation bar floating above the gesture pill with bare
                // background behind it. Each chrome surface applies its own
                // insets instead (top bar = status bar, nav bar = navigation bar).
                RootScreen(
                    viewModel = vm,
                    // Debug-only: lets scripts/screenshots.sh open a specific
                    // tab instead of tapping coordinates that move at every
                    // screen size. Ignored in release so it can't be driven
                    // from outside.
                    startDestination = Destination.fromKey(
                        intent?.getStringExtra(EXTRA_SCREEN),
                    )?.takeIf { BuildConfig.DEBUG } ?: Destination.Home,
                    onShareDiagnostics = { file ->
                        val intent = DiagnosticsExporter.shareIntent(this@MainActivity, file)
                        startActivity(Intent.createChooser(intent, "Share diagnostics"))
                    },
                )
            }
        }
    }
}

package com.meerkly.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meerkly.android.diagnostics.DiagnosticsExporter
import com.meerkly.android.ui.MainViewModel
import com.meerkly.android.ui.RootScreen
import com.meerkly.android.ui.theme.MeerklyTheme
import com.meerkly.android.worker.WorkerServiceLauncher

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        // App is foregrounded — the always-legal moment to (re)raise the worker
        // service. No-ops unless enabled + paired (WorkerServiceLauncher).
        WorkerServiceLauncher.startIfEligible(this, (application as MeerklyApp).graph)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeerklyTheme {
                val vm: MainViewModel = viewModel()
                // No Scaffold: it would inset the whole tree uniformly, leaving a
                // bottom navigation bar floating above the gesture pill with bare
                // background behind it. Each chrome surface applies its own
                // insets instead (top bar = status bar, nav bar = navigation bar).
                RootScreen(
                    viewModel = vm,
                    onShareDiagnostics = { file ->
                        val intent = DiagnosticsExporter.shareIntent(this@MainActivity, file)
                        startActivity(Intent.createChooser(intent, "Share diagnostics"))
                    },
                )
            }
        }
    }
}

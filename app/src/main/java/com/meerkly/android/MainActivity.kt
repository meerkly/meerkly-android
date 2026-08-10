package com.meerkly.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
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
    }
}

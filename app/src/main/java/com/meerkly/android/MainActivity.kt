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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meerkly.android.diagnostics.DiagnosticsExporter
import com.meerkly.android.ui.MainScreen
import com.meerkly.android.ui.MainViewModel
import com.meerkly.android.ui.theme.MeerklyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeerklyTheme {
                val vm: MainViewModel = viewModel()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = vm,
                        onShareDiagnostics = { file ->
                            val intent = DiagnosticsExporter.shareIntent(this, file)
                            startActivity(Intent.createChooser(intent, "Share diagnostics"))
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

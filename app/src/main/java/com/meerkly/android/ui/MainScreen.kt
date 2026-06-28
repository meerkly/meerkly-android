package com.meerkly.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meerkly.android.browser.GeckoViewHost
import com.meerkly.android.model.BrowserStatus
import com.meerkly.android.model.NavigationResult
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onShareDiagnostics: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status by viewModel.status.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val scope = rememberCoroutineScope()
    var url by remember {
        mutableStateOf(
            "https://www.google.com/search?q=casino+bonus" +
                "&uule=w+CAIQICISVsOkc3RlcsOlcywgU3dlZGVu&gl=se&hl=sv"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.onOpen(url) }) { Text("Open") }
            OutlinedButton(onClick = { viewModel.onStop() }) { Text("Stop") }
            OutlinedButton(onClick = { viewModel.onReload() }) { Text("Reload") }
            OutlinedButton(onClick = {
                scope.launch { onShareDiagnostics(viewModel.buildDiagnostics()) }
            }) { Text("Diagnostics") }
        }

        StatusCard(status)

        GeckoViewHost(
            manager = viewModel.browserManager,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        MachineInfoBlock(viewModel.machineInfo)

        Text("Recent logs", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(logs.asReversed()) { entry ->
                Text(
                    text = "[${entry.level}] ${entry.event} ${entry.data}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(status: BrowserStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (status) {
                is BrowserStatus.Idle -> Text("Idle", style = MaterialTheme.typography.titleSmall)
                is BrowserStatus.Loading -> {
                    Text("Loading…", style = MaterialTheme.typography.titleSmall)
                    Text(status.requestedUrl, style = MaterialTheme.typography.bodySmall)
                }
                is BrowserStatus.Success -> {
                    Text("Success", style = MaterialTheme.typography.titleSmall)
                    ResultDetails(status.result)
                }
                is BrowserStatus.Error -> {
                    Text("Error", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    ResultDetails(status.result)
                }
            }
        }
    }
}

@Composable
private fun ResultDetails(result: NavigationResult) {
    result.error?.let { Text("error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    Text("requested: ${result.requestedUrl}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    result.finalUrl?.let { Text("final: $it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    result.title?.let { Text("title: $it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    result.loadedMs?.let { Text("duration: ${it}ms", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun MachineInfoBlock(info: MachineInfo) {
    Text(
        text = "machine ${info.machineId.take(8)}… · v${info.appVersion} · ${info.deviceModel} · " +
            "API ${info.androidSdk} · GeckoView ${info.geckoViewVersion ?: "?"} · ${info.profileStatus}",
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

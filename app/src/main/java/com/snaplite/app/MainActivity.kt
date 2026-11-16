package com.snaplite.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { SnapScreen(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A link shared from another app via the Android "Share" sheet. */
    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val link = extractUrl(i.getStringExtra(Intent.EXTRA_TEXT)) ?: return
        vm.url = link
        vm.analyze()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapScreen(vm: MainViewModel) {
    val items by vm.downloads.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SnapLite") },
                actions = {
                    TextButton(onClick = vm::clearFinished) { Text("Clear") }
                    TextButton(onClick = vm::updateEngine) { Text("Update engine") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { InputSection(vm) }
            items(items, key = { it.id }) { item ->
                DownloadRow(item, onCancel = { vm.cancel(item.id) })
            }
            item {
                Text(
                    "Only download content you own or have permission to save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun InputSection(vm: MainViewModel) {
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = vm.url,
            onValueChange = { vm.url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste video or music link") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clipboard.getText()?.text?.let { vm.url = it } }) { Text("Paste") }
            Button(
                onClick = vm::analyze,
                enabled = !vm.loading && vm.url.isNotBlank(),
            ) { Text(if (vm.loading) "Checking…" else "Analyze") }
        }

        vm.message?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        vm.info?.let { info ->
            InfoCard(info)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !vm.audioOnly, onClick = { vm.audioOnly = false }, label = { Text("Video") })
                FilterChip(selected = vm.audioOnly, onClick = { vm.audioOnly = true }, label = { Text("Audio") })
            }
            if (vm.audioOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mp3", "m4a").forEach { fmt ->
                        FilterChip(
                            selected = vm.audioFormat == fmt,
                            onClick = { vm.audioFormat = fmt },
                            label = { Text(fmt.uppercase()) },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0 to "Best", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")
                        .forEach { (h, label) ->
                            FilterChip(
                                selected = vm.height == h,
                                onClick = { vm.height = h },
                                label = { Text(label) },
                            )
                        }
                }
            }
            Button(onClick = vm::download, modifier = Modifier.fillMaxWidth()) { Text("Download") }
        }
    }
}

@Composable
private fun InfoCard(info: MediaInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 112.dp, height = 63.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column {
                Text(info.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                val meta = listOfNotNull(info.uploader, formatDuration(info.durationSec)).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, onCancel: () -> Unit) {
    val active = item.state == WorkInfo.State.RUNNING || item.state == WorkInfo.State.ENQUEUED

    val status = when (item.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Waiting…"
        WorkInfo.State.RUNNING -> if (item.percent > 0) "${item.percent}%" else "Starting…"
        WorkInfo.State.SUCCEEDED -> "Saved to ${item.folder ?: "your phone"}"
        WorkInfo.State.FAILED -> item.error ?: "Failed"
        WorkInfo.State.CANCELLED -> "Cancelled"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (item.state == WorkInfo.State.RUNNING) {
                if (item.percent > 0) {
                    LinearProgressIndicator(progress = { item.percent / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    status,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.state == WorkInfo.State.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (active) TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

private fun formatDuration(sec: Long): String? {
    if (sec <= 0) return null
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

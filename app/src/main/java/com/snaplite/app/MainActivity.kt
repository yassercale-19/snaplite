package com.snaplite.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkInfo
import coil.compose.AsyncImage

@UnstableApi
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val tools: ToolsViewModel by viewModels()

    private var tab by mutableIntStateOf(0)

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
                Surface(Modifier.fillMaxSize()) {
                    AppRoot(vm, tools, tab) { tab = it }
                }
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
        tab = 0
        vm.url = link
        vm.analyze()
    }
}

private val TABS = listOf(
    "Download" to "⬇️",
    "Library" to "📁",
    "Tools" to "✂️",
    "Settings" to "⚙️",
)

@UnstableApi
@Composable
fun AppRoot(vm: MainViewModel, tools: ToolsViewModel, tab: Int, onTab: (Int) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = { Text(icon) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> HomeScreen(vm, modifier)
            1 -> LibraryScreen(vm, modifier)
            2 -> ToolsScreen(tools, modifier)
            else -> SettingsScreen(vm, modifier)
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("SnapLite", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        }
        item { InputSection(vm) }
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

@Composable
private fun LibraryScreen(vm: MainViewModel, modifier: Modifier) {
    val items by vm.downloads.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Library", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = vm::clearFinished) { Text("Clear finished") }
            }
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "No downloads yet. Paste a link in the Download tab.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items, key = { it.id }) { item ->
            DownloadRow(item, onCancel = { vm.cancel(item.id) })
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Download engine", style = MaterialTheme.typography.titleSmall)
                Text(
                    "yt-dlp updates automatically. Use this if a site stops working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = vm::updateEngine) { Text("Update engine now") }
                vm.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Saved files", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Videos: Movies/SnapLite\nMusic: Music/SnapLite",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "SnapLite 1.1",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

        if (vm.info != null || vm.analyzeFailed) {
            vm.info?.let { InfoCard(it) }
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
            Button(onClick = vm::download, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.info != null) "Download" else "Download anyway")
            }
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
    return formatMs(sec * 1000)
}

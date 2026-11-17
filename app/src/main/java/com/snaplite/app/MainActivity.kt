package com.snaplite.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val tools: ToolsViewModel by viewModels()

    private var tab by mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleLink(intent)

        setContent {
            SnapTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm, tools, tab) { tab = it }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** "Open app" from the share sheet hands the link over here. */
    private fun handleLink(i: Intent?) {
        val link = extractUrl(i?.getStringExtra(EXTRA_LINK)) ?: return
        tab = 0
        vm.url = link
        vm.analyze()
    }

    companion object {
        const val EXTRA_LINK = "link"
    }
}

private data class Tab(val label: String, val icon: @Composable () -> Unit)

@Composable
fun AppRoot(vm: MainViewModel, tools: ToolsViewModel, tab: Int, onTab: (Int) -> Unit) {
    val tabs = listOf(
        Tab("Download") { Icon(painterResource(R.drawable.ic_nav_download), contentDescription = null) },
        Tab("Library") { Icon(painterResource(R.drawable.ic_nav_library), contentDescription = null) },
        Tab("Tools") { Icon(painterResource(R.drawable.ic_nav_tools), contentDescription = null) },
        Tab("Settings") { Icon(Icons.Default.Settings, contentDescription = null) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = t.icon,
                        label = { Text(t.label) },
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
    val clipboard = LocalClipboardManager.current
    val hasResult = vm.info != null || vm.analyzeFailed

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            // Link bar sits in the middle of the screen until a result appears.
            verticalArrangement = if (hasResult) {
                Arrangement.spacedBy(14.dp)
            } else {
                Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasResult) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_download),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Text("SnapLite", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Paste a link to save a video or a song",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            OutlinedTextField(
                value = vm.url,
                onValueChange = { vm.url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste link here") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (vm.url.isBlank()) {
                        TextButton(onClick = {
                            clipboard.getText()?.text?.let {
                                vm.url = it
                                vm.analyze()
                            }
                        }) { Text("Paste") }
                    } else {
                        IconButton(onClick = {
                            vm.url = ""
                            vm.info = null
                            vm.analyzeFailed = false
                            vm.message = null
                        }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                    }
                },
            )

            Button(
                onClick = vm::analyze,
                enabled = !vm.loading && vm.url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) { Text(if (vm.loading) "Checking…" else "Analyze") }

            vm.message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (hasResult) {
                vm.info?.let { InfoCard(it) }
                Column(Modifier.fillMaxWidth()) { FormatOptions(vm) }
                Button(
                    onClick = vm::download,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) { Text(if (vm.info != null) "Download" else "Download anyway") }
            } else {
                Text(
                    "YouTube · TikTok · Instagram · Facebook · X · SoundCloud and more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                "Only download content you own or have permission to save.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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
                verticalAlignment = Alignment.CenterVertically,
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

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Saved files", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Videos: Movies/SnapLite\nMusic: Music/SnapLite\nPhotos: Pictures/SnapLite",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "SnapLite 1.2",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                verticalAlignment = Alignment.CenterVertically,
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

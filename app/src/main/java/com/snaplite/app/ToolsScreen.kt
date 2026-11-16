package com.snaplite.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun ToolsScreen(vm: ToolsViewModel, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.load(uri)
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Video tools", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

        Button(onClick = { picker.launch("video/*") }, enabled = !vm.working) {
            Text(if (vm.uri == null) "Choose a video from your phone" else "Choose another video")
        }

        val uri = vm.uri
        if (uri != null) {
            Preview(uri, vm.startMs)

            Text(
                "Keep from ${formatMs(vm.startMs)} to ${formatMs(vm.endMs)}  (${formatMs(vm.endMs - vm.startMs)})",
                style = MaterialTheme.typography.titleSmall,
            )
            RangeSlider(
                value = vm.startMs.toFloat()..vm.endMs.toFloat(),
                onValueChange = { vm.setRange(it.start.toLong(), it.endInclusive.toLong()) },
                valueRange = 0f..vm.durationMs.toFloat(),
                enabled = !vm.working,
            )

            if (vm.working) {
                LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = vm::cancel) { Text("Cancel") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.export(audioOnly = false) }) { Text("Cut video") }
                    OutlinedButton(onClick = { vm.export(audioOnly = true) }) { Text("Extract audio") }
                }
            }
        }

        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Coming next", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Smart tools: remove silent parts, auto captions, and find the best moments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Preview(uri: Uri, startMs: Long) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(startMs) { player.seekTo(startMs) }

    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}

fun formatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

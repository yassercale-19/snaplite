package com.snaplite.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

        val uri = vm.uri
        if (uri == null) {
            Text(
                "Edit videos right on your phone. Pick a tool, then choose a video.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Op.entries.forEach { op ->
                ToolCard(op) {
                    vm.op = op
                    picker.launch("video/*")
                }
            }
        } else {
            Preview(uri, vm.startMs)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Op.entries.forEach { op ->
                    FilterChip(
                        selected = vm.op == op,
                        onClick = { vm.op = op },
                        enabled = !vm.working,
                        label = { Text(op.label) },
                    )
                }
            }

            Text(vm.op.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (vm.op) {
                Op.COMPRESS -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(720 to "720p", 480 to "480p", 360 to "360p").forEach { (h, label) ->
                        FilterChip(
                            selected = vm.compressHeight == h,
                            onClick = { vm.compressHeight = h },
                            enabled = !vm.working,
                            label = { Text(label) },
                        )
                    }
                }
                Op.ROTATE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(90 to "90°", 180 to "180°", 270 to "270°").forEach { (deg, label) ->
                        FilterChip(
                            selected = vm.rotation == deg,
                            onClick = { vm.rotation = deg },
                            enabled = !vm.working,
                            label = { Text(label) },
                        )
                    }
                }
                else -> Unit
            }

            if (vm.op == Op.FRAME) {
                Text("Frame at ${formatMs(vm.startMs)}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = vm.startMs.toFloat(),
                    onValueChange = { vm.setFrameTime(it.toLong()) },
                    valueRange = 0f..vm.durationMs.toFloat(),
                    enabled = !vm.working,
                )
            } else {
                Text(
                    "Use from ${formatMs(vm.startMs)} to ${formatMs(vm.endMs)}  (${formatMs(vm.endMs - vm.startMs)})",
                    style = MaterialTheme.typography.titleSmall,
                )
                RangeSlider(
                    value = vm.startMs.toFloat()..vm.endMs.toFloat(),
                    onValueChange = { vm.setRange(it.start.toLong(), it.endInclusive.toLong()) },
                    valueRange = 0f..vm.durationMs.toFloat(),
                    enabled = !vm.working,
                )
            }

            if (vm.working && vm.op != Op.FRAME) {
                LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = vm::cancel) { Text("Cancel") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::run, enabled = !vm.working) { Text(vm.op.action) }
                    OutlinedButton(onClick = { picker.launch("video/*") }, enabled = !vm.working) { Text("Other video") }
                }
            }
        }

        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ToolCard(op: Op, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(op.action, style = MaterialTheme.typography.titleSmall)
            Text(
                op.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

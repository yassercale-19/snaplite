package com.snaplite.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

fun formatDuration(sec: Long): String? {
    if (sec <= 0) return null
    return formatMs(sec * 1000)
}

/** Thumbnail + title card for an analyzed link. */
@Composable
fun InfoCard(info: MediaInfo) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
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
                    .clip(RoundedCornerShape(10.dp)),
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

/** Video / Audio switch plus quality or format chips. Shared by the main screen and the share sheet. */
@Composable
fun FormatOptions(vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

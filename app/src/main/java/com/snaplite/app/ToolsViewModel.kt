package com.snaplite.app

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class ToolsViewModel(app: Application) : AndroidViewModel(app) {

    var uri by mutableStateOf<Uri?>(null)
    var durationMs by mutableLongStateOf(0L)
    var startMs by mutableLongStateOf(0L)
    var endMs by mutableLongStateOf(0L)

    var working by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var message by mutableStateOf<String?>(null)

    private var transformer: Transformer? = null

    fun load(u: Uri) {
        viewModelScope.launch {
            message = null
            val duration = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(getApplication<Application>(), u)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                } catch (e: Exception) {
                    null
                } finally {
                    r.release()
                }
            }
            if (duration == null || duration <= 0) {
                message = "Could not read this video"
                return@launch
            }
            uri = u
            durationMs = duration
            startMs = 0
            endMs = duration
        }
    }

    fun setRange(start: Long, end: Long) {
        startMs = start.coerceIn(0, durationMs)
        endMs = end.coerceIn(startMs + 500, durationMs)
    }

    /** Cut the selected range. audioOnly = true saves just the sound as .m4a. */
    fun export(audioOnly: Boolean) {
        val src = uri ?: return
        if (working) return
        if (endMs - startMs < 500) {
            message = "Selected part is too short"
            return
        }
        val ctx = getApplication<Application>()
        val dir = File(ctx.cacheDir, "tools").apply { mkdirs() }
        val out = File(dir, "clip_${System.currentTimeMillis()}." + if (audioOnly) "m4a" else "mp4")

        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(if (endMs >= durationMs) C.TIME_END_OF_SOURCE else endMs)
            .build()
        val item = MediaItem.Builder().setUri(src).setClippingConfiguration(clip).build()
        val edited = EditedMediaItem.Builder(item).setRemoveVideo(audioOnly).build()

        working = true
        progress = 0f
        message = null

        val t = Transformer.Builder(ctx)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    viewModelScope.launch {
                        try {
                            val folder = withContext(Dispatchers.IO) { saveToMediaStore(ctx, out, audioOnly) }
                            message = "Saved to $folder"
                        } catch (e: Exception) {
                            message = cleanError(e)
                        }
                        out.delete()
                        working = false
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    out.delete()
                    message = "Failed: ${exportException.message ?: "unknown error"}"
                    working = false
                }
            })
            .build()
        transformer = t

        try {
            t.start(edited, out.absolutePath)
        } catch (e: Exception) {
            message = cleanError(e)
            working = false
            return
        }

        viewModelScope.launch {
            val holder = ProgressHolder()
            while (working) {
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    progress = holder.progress / 100f
                }
                delay(300)
            }
        }
    }

    fun cancel() {
        transformer?.cancel()
        working = false
        message = "Cancelled"
    }

    override fun onCleared() {
        transformer?.cancel()
        super.onCleared()
    }
}

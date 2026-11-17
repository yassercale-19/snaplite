package com.snaplite.app

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The things people most often look for in a video tool app. */
enum class Op(val label: String, val description: String, val action: String) {
    CUT("Cut", "Trim the start and end of a video", "Cut video"),
    AUDIO("Audio", "Save the sound of a video as an audio file", "Extract audio"),
    COMPRESS("Compress", "Make big videos smaller to share or save space", "Compress"),
    ROTATE("Rotate", "Fix videos that are sideways or upside down", "Rotate video"),
    MUTE("Mute", "Remove the sound from a video", "Remove sound"),
    FRAME("Photo", "Save any frame of a video as a picture", "Save photo"),
}

@OptIn(UnstableApi::class)
class ToolsViewModel(app: Application) : AndroidViewModel(app) {

    var uri by mutableStateOf<Uri?>(null)
    var durationMs by mutableLongStateOf(0L)
    var startMs by mutableLongStateOf(0L)
    var endMs by mutableLongStateOf(0L)

    var op by mutableStateOf(Op.CUT)
    var compressHeight by mutableIntStateOf(720)
    var rotation by mutableIntStateOf(90)

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

    fun closeVideo() {
        if (working) return
        uri = null
        message = null
    }

    fun setRange(start: Long, end: Long) {
        startMs = start.coerceIn(0, durationMs)
        endMs = end.coerceIn(startMs + 500, durationMs)
    }

    fun setFrameTime(ms: Long) {
        startMs = ms.coerceIn(0, durationMs)
    }

    fun run() {
        if (op == Op.FRAME) saveFrame() else export()
    }

    private fun saveFrame() {
        val src = uri ?: return
        if (working) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            working = true
            message = null
            message = try {
                val folder = withContext(Dispatchers.IO) {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(ctx, src)
                        val bmp = r.getFrameAtTime(startMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                            ?: error("Could not read that frame")
                        saveBitmap(ctx, bmp)
                    } finally {
                        r.release()
                    }
                }
                "Saved photo to $folder"
            } catch (e: Exception) {
                cleanError(e)
            }
            working = false
        }
    }

    private fun export() {
        val src = uri ?: return
        if (working) return
        if (endMs - startMs < 500) {
            message = "Selected part is too short"
            return
        }
        val ctx = getApplication<Application>()
        val currentOp = op
        val audioOnly = currentOp == Op.AUDIO
        val dir = File(ctx.cacheDir, "tools").apply { mkdirs() }
        val out = File(
            dir,
            "SnapLite_${currentOp.name.lowercase()}_${System.currentTimeMillis()}." + if (audioOnly) "m4a" else "mp4",
        )

        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(if (endMs >= durationMs) C.TIME_END_OF_SOURCE else endMs)
            .build()
        val item = MediaItem.Builder().setUri(src).setClippingConfiguration(clip).build()

        val editedBuilder = EditedMediaItem.Builder(item)
        val videoEffects = mutableListOf<Effect>()
        when (currentOp) {
            Op.AUDIO -> editedBuilder.setRemoveVideo(true)
            Op.MUTE -> editedBuilder.setRemoveAudio(true)
            Op.COMPRESS -> videoEffects.add(Presentation.createForHeight(compressHeight))
            Op.ROTATE -> videoEffects.add(
                ScaleAndRotateTransformation.Builder().setRotationDegrees(rotation.toFloat()).build()
            )
            else -> Unit
        }
        if (videoEffects.isNotEmpty()) editedBuilder.setEffects(Effects(emptyList(), videoEffects))
        val edited = editedBuilder.build()

        val transformerBuilder = Transformer.Builder(ctx)
        if (currentOp == Op.COMPRESS) {
            val bitrate = when (compressHeight) {
                720 -> 2_500_000
                480 -> 1_200_000
                else -> 700_000
            }
            transformerBuilder
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(ctx)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                        .build()
                )
        }

        working = true
        progress = 0f
        message = null

        val t = transformerBuilder
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

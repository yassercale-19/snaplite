package com.snaplite.app

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

object Keys {
    const val TAG = "snaplite"
    const val URL = "url"
    const val TITLE = "title"
    const val AUDIO = "audio"
    const val AUDIO_FMT = "audio_fmt"
    const val HEIGHT = "height"
    const val PROGRESS = "progress"
    const val ERROR = "error"
    const val FOLDER = "folder"
}

private val URL_REGEX = Regex("""https?://[^\s]+""")

/** Shared text often looks like "Watch this! https://..." – pull out just the link. */
fun extractUrl(text: String?): String? = text?.let { URL_REGEX.find(it)?.value }

fun cleanError(e: Throwable): String =
    e.message?.lines()?.lastOrNull { it.isNotBlank() }?.take(220) ?: "Unknown error"

/**
 * Copies a finished download into the public Music / Movies folder (visible in the
 * gallery, music player and Files app). Returns the folder it was saved to.
 */
fun saveToMediaStore(ctx: Context, file: File, isAudio: Boolean): String {
    val ext = file.extension.lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: if (isAudio) "audio/mpeg" else "video/mp4"

    val (collection, folder) = if (isAudio) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Music/SnapLite"
    } else {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Movies/SnapLite"
    }

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio && mime.startsWith("video")) "audio/mp4" else mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val resolver = ctx.contentResolver
    val uri = resolver.insert(collection, values) ?: error("Could not create the file in storage")
    resolver.openOutputStream(uri)?.use { out ->
        file.inputStream().use { it.copyTo(out) }
    } ?: error("Could not open the output file")

    values.clear()
    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return folder
}

package com.snaplite.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class MediaInfo(
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Long,
)

data class DownloadItem(
    val id: UUID,
    val title: String,
    val state: WorkInfo.State,
    val percent: Int,
    val error: String?,
    val folder: String?,
    val timestamp: Long,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val workManager = WorkManager.getInstance(app)

    var url by mutableStateOf("")
    var info by mutableStateOf<MediaInfo?>(null)
    var loading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    /** True when the link could not be analyzed; the user can still try "Download anyway". */
    var analyzeFailed by mutableStateOf(false)

    var audioOnly by mutableStateOf(false)
    var height by mutableIntStateOf(0) // 0 = best available
    var audioFormat by mutableStateOf("mp3")

    /** After one forced engine update per session, don't keep retrying. */
    private var retriedAfterUpdate = false

    val downloads: StateFlow<List<DownloadItem>> = workManager
        .getWorkInfosByTagFlow(Keys.TAG)
        .map { list -> list.map { it.toItem() }.sortedByDescending { it.timestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun WorkInfo.toItem(): DownloadItem {
        val title = tags.firstOrNull { it.startsWith("t:") }?.drop(2) ?: "Download"
        val ts = tags.firstOrNull { it.startsWith("ts:") }?.drop(3)?.toLongOrNull() ?: 0L
        return DownloadItem(
            id = id,
            title = title,
            state = state,
            percent = progress.getFloat(Keys.PROGRESS, 0f).toInt(),
            error = outputData.getString(Keys.ERROR),
            folder = outputData.getString(Keys.FOLDER),
            timestamp = ts,
        )
    }

    private suspend fun fetchInfo(link: String): MediaInfo {
        val result = withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().getInfo(
                YoutubeDLRequest(link).apply { addOption("--no-playlist") }
            )
        }
        return MediaInfo(
            title = result.title ?: link,
            thumbnail = result.thumbnail,
            uploader = result.uploader,
            durationSec = result.duration.toLong(),
        )
    }

    /** Look up the title / thumbnail of the pasted link. */
    fun analyze() {
        val link = extractUrl(url)
        if (link == null) {
            message = "Paste a valid link first"
            return
        }
        url = link
        viewModelScope.launch {
            loading = true
            message = null
            info = null
            analyzeFailed = false

            val app = getApplication<SnapApp>()
            if (!app.engineReady.isCompleted) message = "Preparing engine…"
            val engineError = app.engineReady.await()
            message = null
            if (engineError != null) {
                message = "Engine failed to start: ${cleanError(engineError)}"
                analyzeFailed = true
                loading = false
                return@launch
            }

            try {
                info = try {
                    fetchInfo(link)
                } catch (e: Exception) {
                    // Sites change often: update yt-dlp once and try again automatically.
                    if (retriedAfterUpdate) throw e
                    retriedAfterUpdate = true
                    message = "Updating engine and retrying…"
                    app.updateEngine(force = true)
                    message = null
                    fetchInfo(link)
                }
            } catch (e: Exception) {
                message = cleanError(e)
                analyzeFailed = true
            }
            loading = false
        }
    }

    fun download() {
        val link = extractUrl(url) ?: return
        val title = info?.title ?: link
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    Keys.URL to link,
                    Keys.TITLE to title,
                    Keys.AUDIO to audioOnly,
                    Keys.AUDIO_FMT to audioFormat,
                    Keys.HEIGHT to height,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(Keys.TAG)
            .addTag("t:" + title.take(100))
            .addTag("ts:" + System.currentTimeMillis())
            .build()
        workManager.enqueue(request)
        message = "Added to downloads"
    }

    fun cancel(id: UUID) {
        workManager.cancelWorkById(id)
    }

    fun clearFinished() {
        workManager.pruneWork()
    }

    /** Manual update button (updates also run automatically every 12 hours). */
    fun updateEngine() {
        viewModelScope.launch {
            message = "Updating engine…"
            val status = getApplication<SnapApp>().updateEngine(force = true)
            message = if (status == null) {
                "Update failed. Check your internet connection."
            } else {
                "Engine update: $status"
            }
        }
    }
}

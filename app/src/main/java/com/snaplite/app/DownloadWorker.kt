package com.snaplite.app

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val taskId = id.toString()

    override suspend fun doWork(): Result {
        val url = inputData.getString(Keys.URL) ?: return Result.failure()
        val title = inputData.getString(Keys.TITLE) ?: "Download"
        val audio = inputData.getBoolean(Keys.AUDIO, false)
        val audioFmt = inputData.getString(Keys.AUDIO_FMT) ?: "mp3"
        val height = inputData.getInt(Keys.HEIGHT, 0)

        val engineError = (applicationContext as SnapApp).engineReady.await()
        if (engineError != null) {
            return Result.failure(workDataOf(Keys.ERROR to "Engine not ready: ${cleanError(engineError)}"))
        }

        setForeground(foregroundInfo(title, 0))

        val tmpDir = File(applicationContext.cacheDir, "dl/$taskId").apply { mkdirs() }

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("-o", "${tmpDir.absolutePath}/%(title).60s [%(id)s].%(ext)s")
            if (audio) {
                addOption("-f", "bestaudio/best")
                addOption("-x")
                addOption("--audio-format", audioFmt)
                addOption("--audio-quality", "0")
            } else {
                // Prefer MP4/M4A so the result plays in every gallery app.
                val res = if (height > 0) "res:$height" else "res"
                addOption("-f", "bv*+ba/b")
                addOption("-S", "$res,ext:mp4:m4a")
                addOption("--merge-output-format", "mp4")
            }
        }

        return try {
            var lastPercent = -1
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(
                    request = request,
                    processId = taskId,
                    callback = { progress, _, _ ->
                        val percent = progress.toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            setProgressAsync(workDataOf(Keys.PROGRESS to percent.toFloat()))
                            setForegroundAsync(foregroundInfo(title, percent))
                        }
                    }
                )
            }

            val result = tmpDir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: return Result.failure(workDataOf(Keys.ERROR to "No file was produced"))

            val folder = saveToMediaStore(applicationContext, result, audio)
            Result.success(workDataOf(Keys.FOLDER to folder))
        } catch (e: Exception) {
            if (isStopped) Result.failure() else Result.failure(workDataOf(Keys.ERROR to cleanError(e)))
        } finally {
            tmpDir.deleteRecursively()
        }
    }


    private fun foregroundInfo(title: String, percent: Int): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, SnapApp.CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (percent > 0) "$percent%" else "Starting…")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()
        return ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

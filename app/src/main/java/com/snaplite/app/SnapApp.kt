package com.snaplite.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnapApp : Application() {

    /** Completes with null when yt-dlp + ffmpeg are ready, or with the error if they failed to start. */
    val engineReady = CompletableDeferred<Throwable?>()

    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        // First start extracts the bundled binaries, so keep it off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            val error = try {
                YoutubeDL.getInstance().init(this@SnapApp)
                FFmpeg.getInstance().init(this@SnapApp)
                null
            } catch (t: Throwable) {
                t
            }
            engineReady.complete(error)
        }
    }

    companion object {
        const val CHANNEL = "downloads"
    }
}

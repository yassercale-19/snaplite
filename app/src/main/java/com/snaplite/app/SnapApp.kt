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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SnapApp : Application() {

    /** Completes with null when yt-dlp + ffmpeg are ready, or with the error if they failed to start. */
    val engineReady = CompletableDeferred<Throwable?>()

    private val updateMutex = Mutex()
    private val prefs by lazy { getSharedPreferences("engine", MODE_PRIVATE) }

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
            // Keep yt-dlp fresh automatically (at most once every 12 hours).
            if (error == null) updateEngine(force = false)
            engineReady.complete(error)
        }
    }

    /**
     * Updates yt-dlp. Returns a short status, or null if the update could not run (e.g. offline).
     * With force = false it does nothing if an update already happened in the last 12 hours.
     */
    suspend fun updateEngine(force: Boolean): String? = updateMutex.withLock {
        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_update", 0L)
        if (!force && now - last < UPDATE_INTERVAL_MS) return@withLock "already up to date"
        try {
            val status = withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().updateYoutubeDL(this@SnapApp)
            }
            prefs.edit().putLong("last_update", now).apply()
            status?.toString() ?: "done"
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val CHANNEL = "downloads"
        private const val UPDATE_INTERVAL_MS = 12L * 60 * 60 * 1000
    }
}

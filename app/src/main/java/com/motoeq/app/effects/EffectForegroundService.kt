package com.motoeq.app.effects

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.motoeq.app.MainActivity
import com.motoeq.app.R
import com.motoeq.app.data.PrefsStore
import kotlinx.coroutines.*

private const val TAG = "EffectForegroundService"

/**
 * Owns the live effect engine only while it's actually attached to something.
 * There's no polling and no broadcast listener here anymore -- this service
 * doesn't start at all until AudioSessionDiscovery.discover() (triggered by
 * the user tapping "Find audio session" in the UI) has already found a
 * session, and the caller passes that session straight in via the start
 * Intent. The service exists, and shows its notification, only for as long
 * as something is attached.
 */
class EffectForegroundService : Service() {

    companion object {
        const val ACTION_ATTACH_SESSION = "com.motoeq.app.ATTACH_SESSION"
        const val ACTION_REFRESH_SETTINGS = "com.motoeq.app.REFRESH_SETTINGS"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PACKAGE_NAME = "package_name"

        private const val CHANNEL_ID = "moteq_status"
        private const val NOTIF_ID = 1001

        @Volatile var instance: EffectForegroundService? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefsStore: PrefsStore
    private val engine = EffectEngine()

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefsStore = PrefsStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ATTACH_SESSION -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (sessionId == -1) {
                    Log.w(TAG, "ATTACH_SESSION with no session id -- ignoring")
                    return START_NOT_STICKY
                }
                // Must call startForeground() promptly after a
                // startForegroundService() call -- do it right here, now that
                // we actually have something to attach to.
                startForeground(NOTIF_ID, buildNotification("Attaching\u2026"))
                scope.launch {
                    val settings = prefsStore.snapshot()
                    engine.attach(sessionId, packageName, settings)
                    updateNotification(packageName)
                }
            }
            ACTION_REFRESH_SETTINGS -> {
                scope.launch { engine.applySettings(prefsStore.snapshot()) }
            }
        }
        return START_NOT_STICKY
    }

    fun applyLiveSettings(settings: PrefsStore.Settings) {
        engine.applySettings(settings)
    }

    fun currentEngine(): EffectEngine = engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "App swiped from recents -- shutting down")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        engine.release()
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MotoEQ status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows which app the equalizer is currently attached to"
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MotoEQ active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_eq_notif)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(attachedPackage: String?) {
        val label = attachedPackage?.let { appLabelFor(it) }
        val text = if (label != null) "Attached to $label" else "Waiting for playback\u2026"
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIF_ID, buildNotification(text))
    }

    private fun appLabelFor(pkg: String): String = try {
        val pm = packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
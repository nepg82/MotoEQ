package com.motoeq.app.effects

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.motoeq.app.MainActivity
import com.motoeq.app.R
import com.motoeq.app.data.PrefsStore
import kotlinx.coroutines.*

class EffectForegroundService : Service() {

    companion object {
        const val ACTION_ATTACH = "com.motoeq.app.ATTACH"
        const val ACTION_DETACH = "com.motoeq.app.DETACH"
        const val ACTION_REFRESH_SETTINGS = "com.motoeq.app.REFRESH_SETTINGS"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PACKAGE_NAME = "package_name"

        private const val CHANNEL_ID = "moteq_status"
        private const val NOTIF_ID = 1001

        // Shared instance so the UI can push live slider changes straight
        // to the currently-attached effect without a round trip through
        // broadcasts. Simple and fine for a single-user local app.
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
        startForeground(NOTIF_ID, buildNotification("Waiting for playback\u2026"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ATTACH -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (sessionId != -1) {
                    scope.launch {
                        val settings = prefsStore.snapshot()
                        engine.attach(sessionId, pkg, settings)
                        updateNotification(pkg)
                    }
                }
            }
            ACTION_DETACH -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                engine.releaseIfSession(sessionId)
                updateNotification(null)
            }
            ACTION_REFRESH_SETTINGS -> {
                scope.launch {
                    engine.applySettings(prefsStore.snapshot())
                }
            }
        }
        return START_STICKY
    }

    /** Called directly by the UI (via [instance]) for live slider feedback. */
    fun applyLiveSettings(settings: PrefsStore.Settings) {
        engine.applySettings(settings)
    }

    fun currentEngine(): EffectEngine = engine

    override fun onBind(intent: Intent?): IBinder? = null

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

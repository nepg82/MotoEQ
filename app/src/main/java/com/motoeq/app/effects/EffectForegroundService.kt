package com.motoeq.app.effects

import android.app.*
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.motoeq.app.MainActivity
import com.motoeq.app.R
import com.motoeq.app.data.PrefsStore
import kotlinx.coroutines.*

private const val TAG = "EffectForegroundService"

class EffectForegroundService : Service() {

    companion object {
        const val ACTION_REFRESH_SETTINGS = "com.motoeq.app.REFRESH_SETTINGS"

        private const val CHANNEL_ID = "moteq_status"
        private const val NOTIF_ID = 1001
        private const val DISCOVERY_POLL_MS = 3000L

        @Volatile var instance: EffectForegroundService? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefsStore: PrefsStore
    private val engine = EffectEngine()
    private var receiverRegistered = false

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (sessionId == -1) return
            val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)

            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    Log.i(TAG, "Broadcast session OPEN: $sessionId from $packageName")
                    scope.launch {
                        val settings = prefsStore.snapshot()
                        engine.attach(sessionId, packageName, settings)
                        updateNotification(packageName)
                    }
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    Log.i(TAG, "Broadcast session CLOSE: $sessionId from $packageName")
                    engine.releaseIfSession(sessionId)
                    updateNotification(null)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefsStore = PrefsStore(applicationContext)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Waiting for playback\u2026"))
        registerSessionReceiver()
        startDiscoveryPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH_SETTINGS) {
            scope.launch { engine.applySettings(prefsStore.snapshot()) }
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
        unregisterSessionReceiver()
        engine.release()
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    private fun registerSessionReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun unregisterSessionReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(sessionReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered", e)
        }
        receiverRegistered = false
    }

    private fun startDiscoveryPolling() {
        scope.launch {
            tryDiscoverAndAttach()
            while (isActive) {
                delay(DISCOVERY_POLL_MS)
                tryDiscoverAndAttach()
            }
        }
    }

    private suspend fun tryDiscoverAndAttach() {
        if (engine.currentSessionId != -1) return

        val activePackage = currentlyPlayingPackage()
        if (activePackage == null) {
            return
        }

        val sessionId = AudioSessionDiscovery.findLikelyActiveSessionId(this)
        if (sessionId == null) {
            Log.d(TAG, "Something is playing ($activePackage) but no session could be discovered yet")
            return
        }

        val settings = prefsStore.snapshot()
        engine.attach(sessionId, activePackage, settings)
        updateNotification(activePackage)
    }

    private fun currentlyPlayingPackage(): String? {
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return null
        val listenerComponent = ComponentName(this, MediaNotificationListenerService::class.java)
        return try {
            manager.getActiveSessions(listenerComponent)
                .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?.packageName
        } catch (e: SecurityException) {
            null
        }
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
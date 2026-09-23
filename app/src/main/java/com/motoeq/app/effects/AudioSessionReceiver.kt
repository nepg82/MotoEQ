package com.motoeq.app.effects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log

private const val TAG = "AudioSessionReceiver"

/**
 * Any app that supports external audio effects (YouTube Music, Spotify,
 * most players) broadcasts AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
 * when it starts playback, and ACTION_CLOSE_... when it stops/tears the
 * session down. This is the whole non-root mechanism — we just have to
 * listen reliably and re-attach every single time instead of only once.
 */
class AudioSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId == -1) return
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)

        val serviceIntent = Intent(context, EffectForegroundService::class.java)

        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "Session OPEN: $sessionId from $packageName")
                serviceIntent.action = EffectForegroundService.ACTION_ATTACH
                serviceIntent.putExtra(EffectForegroundService.EXTRA_SESSION_ID, sessionId)
                serviceIntent.putExtra(EffectForegroundService.EXTRA_PACKAGE_NAME, packageName)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "Session CLOSE: $sessionId from $packageName")
                serviceIntent.action = EffectForegroundService.ACTION_DETACH
                serviceIntent.putExtra(EffectForegroundService.EXTRA_SESSION_ID, sessionId)
            }
            else -> return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

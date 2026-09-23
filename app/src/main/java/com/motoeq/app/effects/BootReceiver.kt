package com.motoeq.app.effects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.motoeq.app.data.PrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val enabled = PrefsStore(context.applicationContext).snapshot().masterEnabled
                if (enabled) {
                    val serviceIntent = Intent(context, EffectForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

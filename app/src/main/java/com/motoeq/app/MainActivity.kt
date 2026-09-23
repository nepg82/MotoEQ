package com.motoeq.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.motoeq.app.data.PrefsStore
import com.motoeq.app.effects.EffectForegroundService
import com.motoeq.app.ui.EqualizerScreen

class MainActivity : ComponentActivity() {

    private lateinit var prefsStore: PrefsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsStore = PrefsStore(applicationContext)

        // Make sure the listener service is alive as soon as the app is opened,
        // so the very next track you start already gets picked up.
        val serviceIntent = Intent(this, EffectForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            MaterialTheme {
                Surface {
                    EqualizerScreen(prefsStore = prefsStore)
                }
            }
        }
    }
}

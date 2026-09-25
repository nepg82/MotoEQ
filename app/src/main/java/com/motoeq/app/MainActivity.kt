package com.motoeq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.motoeq.app.data.PrefsStore
import com.motoeq.app.ui.EqualizerScreen

class MainActivity : ComponentActivity() {

    private lateinit var prefsStore: PrefsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsStore = PrefsStore(applicationContext)

        // Nothing is started here anymore. EffectForegroundService only
        // starts once EqualizerScreen's "Find audio session" tap actually
        // finds something to attach to -- see EqualizerScreen.kt. If the
        // service is already running from earlier (app backgrounded, not
        // swiped away), EqualizerScreen picks that up on its own via
        // EffectForegroundService.instance.

        setContent {
            MaterialTheme {
                Surface {
                    EqualizerScreen(prefsStore = prefsStore)
                }
            }
        }
    }
}
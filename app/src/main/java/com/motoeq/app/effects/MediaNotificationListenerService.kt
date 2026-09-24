package com.motoeq.app.effects

import android.service.notification.NotificationListenerService
import android.util.Log

private const val TAG = "MediaNotifListener"

/**
 * We don't actually care about notification content here. The only reason
 * this class exists is that Android requires an app to have an *enabled*
 * NotificationListenerService before MediaSessionManager.getActiveSessions()
 * will tell it anything -- it's the permission gate, not something we use
 * directly for its stated purpose.
 */
class MediaNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected -- active media sessions are now visible")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Notification listener disconnected")
    }
}
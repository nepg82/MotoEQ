package com.motoeq.app.effects

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "AudioSessionDiscovery"

/** Result of a single on-demand discovery attempt, triggered by the user tapping "Find session". */
data class DiscoveredSession(val sessionId: Int, val packageName: String?)

/**
 * Reads the same low-level system data that apps like Poweramp's equalizer
 * use for their "advanced player tracking" fallback: `dumpsys` output for
 * the audio system, which lists every currently active audio track and its
 * session ID. This is NOT a documented public API -- it's the system's
 * debug dump, readable only by an app holding the DUMP permission (which a
 * third-party app can't request through a normal permission prompt; it has
 * to be granted once via `adb shell pm grant`).
 *
 * Because the exact text format isn't a stable contract, this parses
 * defensively and logs the raw output so the approach can be tuned against
 * a real device if the format here doesn't line up.
 */
object AudioSessionDiscovery {

    fun hasDumpPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.DUMP) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * One-shot, user-triggered lookup: is anything actually playing right now
     * (per the notification listener), and if so, what's the likely session ID
     * (per dumpsys)? Called directly from the UI when the user taps
     * "Find audio session" -- there is no background polling anymore.
     */
    fun discover(context: Context): DiscoveredSession? {
        val activePackage = currentlyPlayingPackage(context) ?: run {
            Log.i(TAG, "Discovery: nothing reports itself as actively playing")
            return null
        }
        val sessionId = findSessionIdForPackage(context, activePackage) ?: run {
            Log.i(TAG, "Discovery: $activePackage is playing but no session id found")
            return null
        }
        return DiscoveredSession(sessionId, activePackage)
    }

    /**
     * Which app the system's active MediaSession list says is currently playing.
     * Requires MediaNotificationListenerService to be enabled under
     * Settings > Notification access -- see the manifest for why.
     */
    fun currentlyPlayingPackage(context: Context): String? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return null
        val listenerComponent = ComponentName(context, MediaNotificationListenerService::class.java)
        return try {
            manager.getActiveSessions(listenerComponent)
                .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?.packageName
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification access -- can't read active sessions", e)
            null
        }
    }

    /**
     * Looks up the exact session ID AudioFlinger has on file for a specific
     * package, via the "Global session refs:" table -- a direct
     * session/pid/uid/package mapping dumpsys prints, rather than guessing
     * from the much noisier per-thread Tracks tables (see git history: an
     * earlier "highest active session" heuristic locked onto the wrong
     * "Session" header entirely and returned another process's PID).
     */
    fun findSessionIdForPackage(context: Context, packageName: String): Int? {
        if (!hasDumpPermission(context)) return null
        val output = runDumpsys("media.audio_flinger") ?: return null
        return parseGlobalSessionRefs(output, packageName)
    }

    private fun runDumpsys(service: String): String? {
        return try {
            val process = ProcessBuilder("dumpsys", service)
                .redirectErrorStream(true)
                .start()
            val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor()
            Log.i(TAG, "dumpsys $service output (${text.length} chars):\n$text")
            text
        } catch (e: Exception) {
            Log.w(TAG, "dumpsys $service failed -- do you have DUMP permission granted?", e)
            null
        }
    }

    /**
     * Parses the "Global session refs:" block:
     *   Global session refs:
     *     session  cnt     pid    uid  name
     *        2593    1    8635  10242  com.google.android.apps.youtube.music
     * Package names don't contain whitespace, so the last token on each
     * data row is the name and the first token is the session id -- no
     * column-index guessing required. Stops at the first blank line after
     * the header, which ends this table.
     */
    private fun parseGlobalSessionRefs(dump: String, packageName: String): Int? {
        val lines = dump.split(Regex("\\r?\\n"))
        val headerIdx = lines.indexOfFirst { it.trim() == "Global session refs:" }
        if (headerIdx == -1) {
            Log.w(TAG, "No 'Global session refs:' section in dumpsys output")
            return null
        }
        // headerIdx + 1 is the "session cnt pid uid name" column header; data starts after that.
        for (i in (headerIdx + 2) until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) break
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size < 2) continue
            if (tokens.last() == packageName) {
                val sessionId = tokens.first().toIntOrNull()
                if (sessionId != null) {
                    Log.i(TAG, "Found session $sessionId for $packageName")
                    return sessionId
                }
            }
        }
        Log.w(TAG, "$packageName not found in Global session refs table")
        return null
    }
}
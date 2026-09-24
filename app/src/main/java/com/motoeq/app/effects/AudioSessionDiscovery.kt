package com.motoeq.app.effects

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "AudioSessionDiscovery"

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
     * Best-effort guess at the audio session ID of whatever is currently
     * actively playing. We don't try to precisely match a session to a
     * specific package here -- that mapping isn't reliably present in this
     * dump across devices/versions. Instead we take the simpler, more
     * robust heuristic: find all currently *active* track sessions, and
     * assume the highest session number is the most recently opened one.
     * This works well for the single-app-playing-at-a-time case (which is
     * the real-world scenario here), and is paired with a MediaSession
     * check by the caller to confirm something is actually playing before
     * this is trusted.
     */
    fun findLikelyActiveSessionId(context: Context): Int? {
        if (!hasDumpPermission(context)) return null

        val output = runDumpsys("media.audio_flinger") ?: return null
        return parseHighestActiveSession(output)
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
     * Looks for the "Tracks" table AudioFlinger prints per output thread.
     * Historically it has a header row containing "Session" as a column
     * name, and data rows are whitespace-separated with a numeric session
     * value in that column. We locate the column by header position rather
     * than a fixed index, since exact spacing/columns can shift slightly
     * across Android versions.
     */
    private fun parseHighestActiveSession(dump: String): Int? {
        var sessionColumnIndex = -1
        var best: Int? = null

        val lines = dump.split(Regex("\\r?\\n"))
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val tokens = line.split(Regex("\\s+"))

            if (sessionColumnIndex == -1) {
                val idx = tokens.indexOfFirst { it == "Session" }
                if (idx != -1) {
                    sessionColumnIndex = idx
                }
                continue
            }

            if (tokens.size <= sessionColumnIndex) continue

            val candidate = tokens[sessionColumnIndex].toIntOrNull() ?: continue
            if (candidate <= 1) continue

            if (best == null || candidate > best!!) {
                best = candidate
            }
        }

        if (best == null) {
            Log.w(TAG, "No 'Session' column found or no active session parsed from dumpsys output")
        } else {
            Log.i(TAG, "Best-guess active session ID: $best")
        }
        return best
    }
}
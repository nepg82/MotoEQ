package com.motoeq.app.effects

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.motoeq.app.data.PrefsStore

private const val TAG = "EffectEngine"

/**
 * Owns the live AudioEffect objects for exactly one audio session at a time.
 * Priority is set to the max (0) so we win over any stock/OEM effect fighting
 * for the same session, which is the usual cause of "my EQ gets overridden"
 * or double-processed/ducked audio.
 */
class EffectEngine {

    var currentSessionId: Int = -1
        private set
    var currentPackage: String? = null
        private set

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    val numberOfBands: Short
        get() = equalizer?.numberOfBands ?: 0

    fun bandFreqRange(band: Short): IntArray? = equalizer?.getBandFreqRange(band)
    fun bandLevelRange(): ShortArray? = equalizer?.bandLevelRange

    /** Attach to a newly opened session and immediately re-apply saved settings. */
    fun attach(sessionId: Int, packageName: String?, settings: PrefsStore.Settings) {
        if (sessionId == currentSessionId) {
            // Same session re-announcing itself — just re-apply in case something reset it.
            applySettings(settings)
            return
        }
        release() // tear down any previous session's effects first

        currentSessionId = sessionId
        currentPackage = packageName

        try {
            equalizer = Equalizer(Int.MAX_VALUE, sessionId).apply {
                enabled = settings.masterEnabled
            }
            loudnessEnhancer = LoudnessEnhancer(sessionId)
            applySettings(settings)
            Log.i(TAG, "Attached to session $sessionId (${packageName ?: "unknown app"})")
        } catch (e: Exception) {
            // Some sessions (e.g. session 0 / global mix, or sessions the OEM
            // audio HAL has locked) can refuse effect attachment. Fail soft.
            Log.w(TAG, "Failed to attach effects to session $sessionId", e)
            release()
        }
    }

    fun applySettings(settings: PrefsStore.Settings) {
        equalizer?.let { eq ->
            eq.enabled = settings.masterEnabled
            if (settings.bandLevels.size.toShort() == eq.numberOfBands) {
                settings.bandLevels.forEachIndexed { i, level ->
                    try {
                        eq.setBandLevel(i.toShort(), level)
                    } catch (e: Exception) {
                        Log.w(TAG, "Bad band level for band $i: $level", e)
                    }
                }
            }
        }
        loudnessEnhancer?.let { le ->
            try {
                le.setTargetGain(settings.loudnessGainMb)
                le.enabled = settings.masterEnabled && settings.loudnessEnabled
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer apply failed", e)
            }
        }
    }

    /** Release effects only if they belong to the session that's closing. */
    fun releaseIfSession(sessionId: Int) {
        if (sessionId == currentSessionId) release()
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        equalizer = null
        loudnessEnhancer = null
        currentSessionId = -1
        currentPackage = null
    }
}

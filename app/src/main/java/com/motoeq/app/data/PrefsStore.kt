package com.motoeq.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "moteq_prefs")

/**
 * All effect settings live here so they're applied automatically every time
 * we re-attach to a fresh audio session — you never have to re-set them.
 */
object PrefsKeys {
    val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
    val BAND_LEVELS = stringPreferencesKey("band_levels_mb")   // comma-separated millibels
    val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength") // 0-1000
    val LOUDNESS_GAIN_MB = intPreferencesKey("loudness_gain_mb")       // millibels, e.g. 0-2000
    val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
    val LOUDNESS_ENABLED = booleanPreferencesKey("loudness_enabled")
}

class PrefsStore(private val context: Context) {

    val masterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[PrefsKeys.MASTER_ENABLED] ?: true }

    val bandLevels: Flow<List<Short>> =
        context.dataStore.data.map { prefs ->
            prefs[PrefsKeys.BAND_LEVELS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { it.toShort() }
                ?: emptyList()
        }

    val bassBoostStrength: Flow<Int> =
        context.dataStore.data.map { it[PrefsKeys.BASS_BOOST_STRENGTH] ?: 0 }

    val bassBoostEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[PrefsKeys.BASS_BOOST_ENABLED] ?: false }

    val loudnessGainMb: Flow<Int> =
        context.dataStore.data.map { it[PrefsKeys.LOUDNESS_GAIN_MB] ?: 0 }

    val loudnessEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[PrefsKeys.LOUDNESS_ENABLED] ?: false }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefsKeys.MASTER_ENABLED] = enabled }
    }

    suspend fun setBandLevels(levels: List<Short>) {
        context.dataStore.edit {
            it[PrefsKeys.BAND_LEVELS] = levels.joinToString(",")
        }
    }

    suspend fun setBassBoost(strength: Int, enabled: Boolean) {
        context.dataStore.edit {
            it[PrefsKeys.BASS_BOOST_STRENGTH] = strength
            it[PrefsKeys.BASS_BOOST_ENABLED] = enabled
        }
    }

    suspend fun setLoudness(gainMb: Int, enabled: Boolean) {
        context.dataStore.edit {
            it[PrefsKeys.LOUDNESS_GAIN_MB] = gainMb
            it[PrefsKeys.LOUDNESS_ENABLED] = enabled
        }
    }

    // Synchronous snapshot, used by the service when a new session opens
    // and we need the last-saved values immediately (no UI collecting yet).
    suspend fun snapshot(): Settings {
        val prefs = context.dataStore.data.first()
        return Settings(
            masterEnabled = prefs[PrefsKeys.MASTER_ENABLED] ?: true,
            bandLevels = prefs[PrefsKeys.BAND_LEVELS]
                ?.split(",")?.filter { it.isNotBlank() }?.map { it.toShort() } ?: emptyList(),
            bassBoostStrength = prefs[PrefsKeys.BASS_BOOST_STRENGTH] ?: 0,
            bassBoostEnabled = prefs[PrefsKeys.BASS_BOOST_ENABLED] ?: false,
            loudnessGainMb = prefs[PrefsKeys.LOUDNESS_GAIN_MB] ?: 0,
            loudnessEnabled = prefs[PrefsKeys.LOUDNESS_ENABLED] ?: false
        )
    }

    data class Settings(
        val masterEnabled: Boolean,
        val bandLevels: List<Short>,
        val bassBoostStrength: Int,
        val bassBoostEnabled: Boolean,
        val loudnessGainMb: Int,
        val loudnessEnabled: Boolean
    )
}

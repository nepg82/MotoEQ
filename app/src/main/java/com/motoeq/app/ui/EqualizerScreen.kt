package com.motoeq.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motoeq.app.data.PrefsStore
import com.motoeq.app.effects.EffectForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Fallback layout used until we've actually attached to a live session and
// learned the device's real band count/frequency/range from the Equalizer
// effect itself (this varies slightly by device audio HAL).
private val DEFAULT_BAND_HZ_LABELS = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
private const val DEFAULT_LEVEL_RANGE = 1500 // +/- millibels

@Composable
fun EqualizerScreen(prefsStore: PrefsStore) {
    val scope = rememberCoroutineScope()

    val masterEnabled by prefsStore.masterEnabled.collectAsState(initial = true)
    val savedBandLevels by prefsStore.bandLevels.collectAsState(initial = emptyList())
    val loudnessGain by prefsStore.loudnessGainMb.collectAsState(initial = 0)
    val loudnessEnabled by prefsStore.loudnessEnabled.collectAsState(initial = false)

    val bandCount = DEFAULT_BAND_HZ_LABELS.size
    var bandLevels by remember(savedBandLevels) {
        mutableStateOf(
            if (savedBandLevels.size == bandCount) savedBandLevels
            else List(bandCount) { 0.toShort() }
        )
    }

    // The service doesn't expose a Flow for attach status, so we poll lightly
    // to keep the "Attached to..." line honest without wiring up more plumbing.
    var statusTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            statusTick++
        }
    }
    val engine = remember(statusTick) { EffectForegroundService.instance?.currentEngine() }
    val attachedPackage = engine?.currentPackage
    val statusText = if (attachedPackage != null) {
        "Attached \u2022 session ${engine.currentSessionId} \u2022 $attachedPackage"
    } else {
        "Not attached yet \u2014 start playing music to connect"
    }

    fun currentSettings() = PrefsStore.Settings(
        masterEnabled = masterEnabled,
        bandLevels = bandLevels,
        loudnessGainMb = loudnessGain,
        loudnessEnabled = loudnessEnabled
    )

    fun pushLive() {
        EffectForegroundService.instance?.applyLiveSettings(currentSettings())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("MotoEQ", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(statusText, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Equalizer enabled", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = masterEnabled,
                onCheckedChange = { checked ->
                    scope.launch { prefsStore.setMasterEnabled(checked) }
                    pushLive()
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        DEFAULT_BAND_HZ_LABELS.forEachIndexed { index, label ->
            BandSlider(
                label = label,
                value = bandLevels[index],
                range = DEFAULT_LEVEL_RANGE,
                onValueChange = { newVal ->
                    bandLevels = bandLevels.toMutableList().also { it[index] = newVal }
                    pushLive()
                },
                onValueChangeFinished = {
                    scope.launch { prefsStore.setBandLevels(bandLevels) }
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Loudness boost", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = loudnessEnabled,
                onCheckedChange = { checked ->
                    scope.launch { prefsStore.setLoudness(loudnessGain, checked) }
                    pushLive()
                }
            )
        }
        Text(
            "Use this instead of the OS media volume if your helmet comms unit " +
                    "still sounds quiet at max volume. Start low \u2014 this is a digital " +
                    "gain stage and can clip/distort if pushed too far.",
            style = MaterialTheme.typography.bodySmall
        )
        LoudnessSlider(
            value = loudnessGain,
            onChange = { v ->
                scope.launch { prefsStore.setLoudness(v, loudnessEnabled) }
                pushLive()
            }
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BandSlider(
    label: String,
    value: Short,
    range: Int,
    onValueChange: (Short) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${value / 100} dB", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat(),
            valueRange = -range.toFloat()..range.toFloat(),
            onValueChange = { onValueChange(it.toInt().toShort()) },
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

@Composable
private fun LoudnessSlider(value: Int, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Slider(
        value = local,
        valueRange = 0f..2000f,
        onValueChange = { local = it },
        onValueChangeFinished = { onChange(local.toInt()) }
    )
}

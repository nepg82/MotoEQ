package com.motoeq.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.motoeq.app.data.PrefsStore
import com.motoeq.app.effects.AudioSessionDiscovery
import com.motoeq.app.effects.EffectForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DEFAULT_BAND_HZ_LABELS = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
private const val DEFAULT_LEVEL_RANGE = 1500

@Composable
fun EqualizerScreen(prefsStore: PrefsStore) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

    val hasDumpPermission = remember(statusTick) { AudioSessionDiscovery.hasDumpPermission(context) }
    val hasNotificationAccess = remember(statusTick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun currentSettings() = PrefsStore.Settings(
        masterEnabled = masterEnabled,
        bandLevels = bandLevels,
        loudnessGainMb = loudnessGain,
        loudnessEnabled = loudnessEnabled
    )

    fun pushLive(override: (PrefsStore.Settings) -> PrefsStore.Settings = { it }) {
        EffectForegroundService.instance?.applyLiveSettings(override(currentSettings()))
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

        if (!hasNotificationAccess || !hasDumpPermission) {
            Spacer(Modifier.height(12.dp))
            SetupWarningCard(
                hasNotificationAccess = hasNotificationAccess,
                hasDumpPermission = hasDumpPermission,
                onGrantNotificationAccess = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
        }

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
                    pushLive { it.copy(masterEnabled = checked) }
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
                    pushLive { it.copy(loudnessEnabled = checked) }
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
                pushLive { it.copy(loudnessGainMb = v) }
            }
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SetupWarningCard(
    hasNotificationAccess: Boolean,
    hasDumpPermission: Boolean,
    onGrantNotificationAccess: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Setup needed for catching music already in progress",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            if (!hasNotificationAccess) {
                Text(
                    "\u2022 Notification access not granted yet",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onGrantNotificationAccess) {
                    Text("Grant notification access")
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!hasDumpPermission) {
                Text(
                    "\u2022 DUMP permission not granted \u2014 this one can't be granted from " +
                            "a button in the app. See the README for the one-time ADB command.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
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
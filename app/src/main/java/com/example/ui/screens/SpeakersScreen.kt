package com.example.ui.screens

import android.Manifest
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.SpeakerSnapshot
import com.example.ui.theme.CalmGreen

@Composable
fun SpeakersScreen(
    snapshot: SpeakerSnapshot,
    onRefresh: () -> Unit,
    onSetVolume: (streamType: Int, value: Int) -> Unit,
    onToggleMediaMute: () -> Unit,
    onRouteToPhoneSpeaker: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onRefresh()
    }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "ဤစာမျက်နှာမှ ဖုန်းနှင့် Paired/ချိတ်ဆက်ခွင့်ပြုထားသော Bluetooth စပီကာများနှင့် စက်တွင်းအသံစနစ်များကို တိုက်ရိုက် အတိုးအချဲ့/အသံပိတ် ထိန်းချုပ်နိုင်ပါသည်။ (ခွင့်ပြုချက်မရှိသော ပြင်ပသူစိမ်းဆောင်းဘောက်များကို အဝေးမှ ထိန်းချုပ်၍မရပါ။)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "စပီကာနှင့် အသံအတိုးအချဲ့ ထိန်းချုပ်မှု",
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("refresh_speakers_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh speaker status"
                            )
                        }
                    }

                    VolumeSliderRow(
                        label = "Speaker / Media (ဆောင်းဘောက် / မီဒီယာ)",
                        current = snapshot.mediaVolume,
                        max = snapshot.mediaMax,
                        onValueChange = { onSetVolume(AudioManager.STREAM_MUSIC, it) },
                        testTag = "media_volume_slider"
                    )

                    VolumeSliderRow(
                        label = "Ring & Notification (ဖုန်းမြည်သံ)",
                        current = snapshot.ringVolume,
                        max = snapshot.ringMax,
                        onValueChange = { onSetVolume(AudioManager.STREAM_RING, it) },
                        testTag = "ring_volume_slider"
                    )

                    VolumeSliderRow(
                        label = "Alarm Volume (နှိုးစက်အသံ)",
                        current = snapshot.alarmVolume,
                        max = snapshot.alarmMax,
                        onValueChange = { onSetVolume(AudioManager.STREAM_ALARM, it) },
                        testTag = "alarm_volume_slider"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onToggleMediaMute,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("mute_speaker_button")
                        ) {
                            Icon(
                                imageVector = if (snapshot.isMediaMuted) {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                },
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (snapshot.isMediaMuted) "အသံပြန်ဖွင့်" else "အသံပိတ် (Mute)")
                        }

                        OutlinedButton(
                            onClick = onRouteToPhoneSpeaker,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("route_phone_speaker_button")
                        ) {
                            Icon(imageVector = Icons.Filled.PhoneAndroid, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ဖုန်းစပီကာသို့")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Paired Bluetooth စပီကာများ",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Ringer Mode: ${snapshot.ringerModeLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenBluetoothSettings,
                            modifier = Modifier.testTag("open_bt_settings_button")
                        ) {
                            Icon(imageVector = Icons.Filled.SettingsBluetooth, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("BT ချိတ်ဆက်/ဖြတ်")
                        }
                    }

                    if (!snapshot.bluetoothPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(
                            onClick = {
                                btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("request_bt_permission_button")
                        ) {
                            Icon(imageVector = Icons.Filled.Bluetooth, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bluetooth စပီကာစာရင်းကြည့်ရန် ခွင့်ပြုချက်ပေးမည်")
                        }
                    } else if (!snapshot.bluetoothEnabled) {
                        Text(
                            text = "Bluetooth ပိတ်ထားပါသည်။ စပီကာများချိတ်ဆက်ရန် BT ချိတ်ဆက်/ဖြတ် ခလုတ်မှ ဖွင့်ပါ။",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (snapshot.pairedBluetoothSpeakers.isEmpty()) {
                        Text(
                            text = "ချိတ်ဆက်ထားဖူးသော (Paired) Bluetooth စပီကာ မတွေ့ရှိသေးပါ။",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        snapshot.pairedBluetoothSpeakers.forEach { speaker ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (speaker.isConnected) {
                                            CalmGreen.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (speaker.isConnected) {
                                                    Icons.Filled.BluetoothConnected
                                                } else {
                                                    Icons.Filled.Bluetooth
                                                },
                                                contentDescription = null,
                                                tint = if (speaker.isConnected) {
                                                    CalmGreen
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = speaker.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${speaker.deviceTypeLabel} • " +
                                                if (speaker.isConnected) "Connected" else "Paired (Standby)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "လက်ရှိအသံထွက်ပေါက်များ (Audio Outputs)",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(snapshot.outputDevices, key = { it.id }) { device ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speaker,
                        contentDescription = null,
                        tint = if (device.isExternalSpeaker) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = device.typeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeSliderRow(
    label: String,
    current: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    testTag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${((current * 100f) / max).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.testTag(testTag)
        )
    }
}

package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class AudioOutputInfo(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isExternalSpeaker: Boolean
)

data class PairedSpeakerInfo(
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val deviceTypeLabel: String
)

data class SpeakerSnapshot(
    val mediaVolume: Int,
    val mediaMax: Int,
    val isMediaMuted: Boolean,
    val ringVolume: Int,
    val ringMax: Int,
    val alarmVolume: Int,
    val alarmMax: Int,
    val ringerModeLabel: String,
    val outputDevices: List<AudioOutputInfo>,
    val pairedBluetoothSpeakers: List<PairedSpeakerInfo>,
    val bluetoothPermissionGranted: Boolean,
    val bluetoothEnabled: Boolean
)

class SpeakerControlManager(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var savedMediaVolumeBeforeAutoGuard: Int? = null

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun readSnapshot(): SpeakerSnapshot {
        val mediaVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val isMediaMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC) || mediaVol == 0

        val ringVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)

        val alarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)

        val ringerModeLabel = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent (အသံပိတ်)"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate (တုန်ခါမှု)"
            else -> "Normal (ပုံမှန်)"
        }

        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { dev ->
            val typeName = mapAudioDeviceType(dev.type)
            val external = dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG
            AudioOutputInfo(
                id = dev.id,
                name = dev.productName?.toString()?.ifBlank { typeName } ?: typeName,
                typeLabel = typeName,
                isExternalSpeaker = external
            )
        }.distinctBy { "${it.name}-${it.typeLabel}" }

        val btPermission = hasBluetoothPermission()
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        val btEnabled = adapter?.isEnabled == true

        val pairedSpeakers = if (btPermission && adapter != null && btEnabled) {
            try {
                val connectedAddresses = try {
                    bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)
                        ?.map { it.address }?.toSet() ?: emptySet()
                } catch (_: Exception) {
                    emptySet()
                }
                val activeOutputNames = outputs.filter { it.isExternalSpeaker }.map { it.name.lowercase() }.toSet()

                adapter.bondedDevices.orEmpty()
                    .filter { isAudioDevice(it) }
                    .map { device ->
                        val devName = device.name ?: "Bluetooth Speaker"
                        val isConn = isDeviceConnected(device) ||
                            connectedAddresses.contains(device.address) ||
                            activeOutputNames.any { it.contains(devName.lowercase()) }
                        PairedSpeakerInfo(
                            name = devName,
                            address = device.address ?: "Unknown",
                            isConnected = isConn,
                            deviceTypeLabel = mapBluetoothClass(device)
                        )
                    }
                    .sortedByDescending { it.isConnected }
            } catch (_: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }

        return SpeakerSnapshot(
            mediaVolume = mediaVol,
            mediaMax = mediaMax,
            isMediaMuted = isMediaMuted,
            ringVolume = ringVol,
            ringMax = ringMax,
            alarmVolume = alarmVol,
            alarmMax = alarmMax,
            ringerModeLabel = ringerModeLabel,
            outputDevices = outputs,
            pairedBluetoothSpeakers = pairedSpeakers,
            bluetoothPermissionGranted = btPermission,
            bluetoothEnabled = btEnabled
        )
    }

    fun setStreamVolume(streamType: Int, targetVolume: Int) {
        val max = audioManager.getStreamMaxVolume(streamType)
        val clamped = targetVolume.coerceIn(0, max)
        audioManager.setStreamVolume(streamType, clamped, 0)
    }

    fun toggleMediaMute() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) {
            savedMediaVolumeBeforeAutoGuard = current
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } else {
            val restoreTo = (savedMediaVolumeBeforeAutoGuard ?: (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 3))
                .coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreTo, 0)
        }
    }

    fun applyAutoGuardReduction(): Boolean {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val safeQuietCap = (max * 0.2f).toInt().coerceAtLeast(0)
        if (current > safeQuietCap) {
            if (savedMediaVolumeBeforeAutoGuard == null) {
                savedMediaVolumeBeforeAutoGuard = current
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, safeQuietCap, 0)
            return true
        }
        return false
    }

    fun routeToBuiltInSpeaker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
        }
    }

    fun openSystemBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun isAudioDevice(device: BluetoothDevice): Boolean {
        val majorClass = device.bluetoothClass?.majorDeviceClass
        return majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            majorClass == BluetoothClass.Device.Major.UNCATEGORIZED
    }

    @SuppressLint("MissingPermission")
    private fun mapBluetoothClass(device: BluetoothDevice): String {
        return when (device.bluetoothClass?.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Loudspeaker / Soundbox"
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Headset"
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "Hi-Fi Audio System"
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> "Portable Speaker"
            else -> "Bluetooth Audio"
        }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun mapAudioDeviceType(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Speaker (A2DP)"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Call Audio"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
            else -> "Audio Output"
        }
    }
}

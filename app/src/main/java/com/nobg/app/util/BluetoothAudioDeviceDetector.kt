package com.nobg.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nobg.app.data.SelectedBluetoothDeviceEntity
import java.util.Locale

data class ConnectedBluetoothAudioDevice(
    val address: String,
    val name: String
)

object BluetoothAudioDeviceDetector {

    fun getConnectedAudioDevices(context: Context): List<ConnectedBluetoothAudioDevice> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return emptyList()

        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .asSequence()
            .filter { it.type.isBluetoothAudioType() }
            .map { device ->
                ConnectedBluetoothAudioDevice(
                    address = normalizeAddress(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.address else ""
                    ),
                    name = normalizeName(device.productName?.toString().orEmpty())
                )
            }
            .distinct()
            .toList()
    }

    fun isConnected(
        savedAddress: String,
        savedName: String,
        connectedDevices: List<ConnectedBluetoothAudioDevice>
    ): Boolean {
        val address = normalizeAddress(savedAddress)
        val name = normalizeName(savedName)
        return connectedDevices.any { connected ->
            (address.isNotBlank() && connected.address.isNotBlank() && connected.address == address) ||
                (name.isNotBlank() && connected.name.isNotBlank() && connected.name == name)
        }
    }

    fun hasSelectedDeviceConnected(
        selectedDevices: List<SelectedBluetoothDeviceEntity>,
        connectedDevices: List<ConnectedBluetoothAudioDevice>
    ): Boolean = selectedDevices
        .filter { it.isSelected }
        .any { selected ->
            isConnected(selected.address, selected.name, connectedDevices)
        }

    private fun Int.isBluetoothAudioType(): Boolean = when (this) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID -> true
        else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (this == AudioDeviceInfo.TYPE_BLE_HEADSET || this == AudioDeviceInfo.TYPE_BLE_SPEAKER)
    }

    private fun normalizeAddress(address: String): String = address
        .trim()
        .replace(":", "")
        .replace("-", "")
        .uppercase(Locale.ROOT)

    private fun normalizeName(name: String): String = name
        .trim()
        .lowercase(Locale.ROOT)
}

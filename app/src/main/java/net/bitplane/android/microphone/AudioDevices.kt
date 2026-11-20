package net.bitplane.android.microphone

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A choice in a device menu. A null [key] means "let Android pick". */
data class DeviceOption(val key: String?, val label: String)

/**
 * A user-chosen audio device in one direction (input or output). The choice is kept as a key
 * string rather than an AudioDeviceInfo, because device IDs change every time something is
 * plugged in or reconnected.
 */
class DeviceChoice internal constructor(
    private val prefKey: String,
    private val direction: Int,
    private val supportedTypes: Set<Int>,
    private val describe: (Context, AudioDeviceInfo, List<AudioDeviceInfo>) -> String,
) {
    private val _selected = MutableStateFlow<String?>(null)
    private var loaded = false

    /** Key of the chosen device, or null for automatic. */
    val selected: StateFlow<String?> = _selected.asStateFlow()

    fun load(context: Context) {
        if (loaded) return
        _selected.value = AudioDevices.prefs(context).getString(prefKey, null)
        loaded = true
    }

    fun select(context: Context, key: String?) {
        _selected.value = key
        AudioDevices.prefs(context).edit {
            if (key == null) remove(prefKey) else putString(prefKey, key)
        }
    }

    fun available(context: Context): List<AudioDeviceInfo> =
        context.getSystemService(AudioManager::class.java)
            .getDevices(direction)
            .filter { it.type in supportedTypes }

    /** The chosen device if it's connected right now, otherwise null (automatic). */
    fun current(context: Context): AudioDeviceInfo? =
        selected.value?.let { key -> available(context).firstOrNull { AudioDevices.keyOf(it) == key } }

    fun options(context: Context): List<DeviceOption> {
        val devices = available(context)
        return listOf(DeviceOption(null, context.getString(R.string.automatic))) +
            devices.map { DeviceOption(AudioDevices.keyOf(it), describe(context, it, devices)) }
                .distinctBy { it.key }
    }
}

object AudioDevices {
    private const val PREFS_NAME = "Microphone"

    val input = DeviceChoice(
        prefKey = "inputDevice",
        direction = AudioManager.GET_DEVICES_INPUTS,
        supportedTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BUILTIN_MIC)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_DEVICE)
            add(AudioDeviceInfo.TYPE_USB_ACCESSORY)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_LINE_ANALOG)
            add(AudioDeviceInfo.TYPE_LINE_DIGITAL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        },
        describe = ::describeInput,
    )

    // Bluetooth SCO ("call audio") outputs are left out: media plays over A2DP, and a headset
    // can't do both at once.
    val output = DeviceChoice(
        prefKey = "outputDevice",
        direction = AudioManager.GET_DEVICES_OUTPUTS,
        supportedTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_USB_DEVICE)
            add(AudioDeviceInfo.TYPE_USB_ACCESSORY)
            add(AudioDeviceInfo.TYPE_LINE_ANALOG)
            add(AudioDeviceInfo.TYPE_LINE_DIGITAL)
            add(AudioDeviceInfo.TYPE_AUX_LINE)
            add(AudioDeviceInfo.TYPE_HDMI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        },
        describe = ::describeOutput,
    )

    fun load(context: Context) {
        input.load(context)
        output.load(context)
    }

    fun keyOf(device: AudioDeviceInfo): String =
        "${device.type}|${addressOf(device)}|${device.productName}"

    /** Bluetooth headset mics only work with the phone in communication ("call") mode. */
    fun needsCommunicationMode(device: AudioDeviceInfo): Boolean =
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    internal fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun describeInput(context: Context, device: AudioDeviceInfo, all: List<AudioDeviceInfo>): String =
        when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> {
                // Phones with several mics report where each one is, e.g. "bottom" or "back".
                val base = context.getString(R.string.input_phone)
                val address = addressOf(device)
                val many = all.count { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC } > 1
                if (many && address.isNotBlank()) "$base ($address)" else base
            }
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(R.string.input_headset)
            else -> nameOf(device)
        }

    @Suppress("UNUSED_PARAMETER")
    private fun describeOutput(context: Context, device: AudioDeviceInfo, all: List<AudioDeviceInfo>): String =
        when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.output_speaker)
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> context.getString(R.string.output_headphones)
            else -> nameOf(device)
        }

    /** External devices are best described by their own product name. */
    private fun nameOf(device: AudioDeviceInfo): String =
        device.productName?.toString().orEmpty().ifBlank {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_AUX_LINE -> "Line"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
                else -> if (needsCommunicationMode(device)) "Bluetooth" else "USB"
            }
        }

    // AudioDeviceInfo.address only exists from Android 9.
    private fun addressOf(device: AudioDeviceInfo): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.address else ""
}

package net.bitplane.android.microphone

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A choice in the input menu. A null [key] means "let Android pick". */
data class InputOption(val key: String?, val label: String)

/**
 * Which microphone to record from. The user's choice is kept as a key string rather than an
 * AudioDeviceInfo, because device IDs change every time something is plugged in or reconnected.
 */
object AudioInputs {
    private const val PREFS_NAME = "Microphone"
    private const val KEY_INPUT = "inputDevice"

    private val supportedTypes = buildSet {
        add(AudioDeviceInfo.TYPE_BUILTIN_MIC)
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_DEVICE)
        add(AudioDeviceInfo.TYPE_USB_ACCESSORY)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        add(AudioDeviceInfo.TYPE_LINE_ANALOG)
        add(AudioDeviceInfo.TYPE_LINE_DIGITAL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    private val _selected = MutableStateFlow<String?>(null)
    private var loaded = false

    /** Key of the chosen input, or null for automatic. */
    val selected: StateFlow<String?> = _selected.asStateFlow()

    fun load(context: Context) {
        if (loaded) return
        _selected.value = prefs(context).getString(KEY_INPUT, null)
        loaded = true
    }

    fun select(context: Context, key: String?) {
        _selected.value = key
        prefs(context).edit {
            if (key == null) remove(KEY_INPUT) else putString(KEY_INPUT, key)
        }
    }

    fun available(context: Context): List<AudioDeviceInfo> =
        context.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type in supportedTypes }

    fun find(context: Context, key: String): AudioDeviceInfo? =
        available(context).firstOrNull { keyOf(it) == key }

    fun keyOf(device: AudioDeviceInfo): String =
        "${device.type}|${addressOf(device)}|${device.productName}"

    /** Bluetooth headset mics only work with the phone in communication ("call") mode. */
    fun needsCommunicationMode(device: AudioDeviceInfo): Boolean =
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    fun options(context: Context): List<InputOption> {
        val devices = available(context)
        val builtinCount = devices.count { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        return listOf(InputOption(null, context.getString(R.string.input_automatic))) +
            devices.map { InputOption(keyOf(it), label(context, it, builtinCount > 1)) }
                .distinctBy { it.key }
    }

    private fun label(context: Context, device: AudioDeviceInfo, manyBuiltins: Boolean): String {
        val name = device.productName?.toString().orEmpty()
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> {
                val base = context.getString(R.string.input_phone)
                // Phones with several mics report where each one is, e.g. "bottom" or "back".
                val address = addressOf(device)
                if (manyBuiltins && address.isNotBlank()) "$base ($address)" else base
            }
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(R.string.input_headset)
            else -> name.ifBlank {
                when {
                    needsCommunicationMode(device) -> "Bluetooth"
                    device.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                        device.type == AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line in"
                    else -> "USB"
                }
            }
        }
    }

    // AudioDeviceInfo.address only exists from Android 9.
    private fun addressOf(device: AudioDeviceInfo): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.address else ""

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

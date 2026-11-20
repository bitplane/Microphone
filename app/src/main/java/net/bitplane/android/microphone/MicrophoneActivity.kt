package net.bitplane.android.microphone

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import net.bitplane.android.microphone.ui.MicrophoneScreen
import net.bitplane.android.microphone.ui.MicrophoneTheme
import net.bitplane.android.microphone.ui.PermissionScreen

class MicrophoneActivity : ComponentActivity() {
    private var hasMicPermission by mutableStateOf(false)
    private var inputs by mutableStateOf(emptyList<DeviceOption>())
    private var outputs by mutableStateOf(emptyList<DeviceOption>())
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    // Keeps the device menus in step with devices being plugged in and unplugged.
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refreshDevices()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refreshDevices()
    }
    private var showPermissionExplanation by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Notifications are nice to have; only the mic is required.
        hasMicPermission = checkMicPermission()
        showPermissionExplanation = !hasMicPermission
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AudioDevices.load(this)
        hasMicPermission = checkMicPermission()
        if (!hasMicPermission && savedInstanceState == null) {
            requestPermissions()
        }

        setContent {
            MicrophoneTheme {
                val active by MicrophoneService.active.collectAsState()
                val selectedInput by AudioDevices.input.selected.collectAsState()
                val selectedOutput by AudioDevices.output.selected.collectAsState()

                if (hasMicPermission) {
                    MicrophoneScreen(
                        active = active,
                        onToggle = { toggle(active) },
                        inputs = inputs,
                        selectedInput = selectedInput,
                        onSelectInput = { AudioDevices.input.select(this, it) },
                        outputs = outputs,
                        selectedOutput = selectedOutput,
                        onSelectOutput = { AudioDevices.output.select(this, it) },
                    )
                } else {
                    PermissionScreen(
                        showExplanation = showPermissionExplanation,
                        onRequestPermissions = { requestPermissions() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        refreshDevices()
    }

    override fun onStop() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        super.onStop()
    }

    private fun refreshDevices() {
        inputs = AudioDevices.input.options(this)
        outputs = AudioDevices.output.options(this)
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted or revoked the permission in system settings.
        hasMicPermission = checkMicPermission()
    }

    private fun toggle(active: Boolean) {
        if (active) {
            MicrophoneService.stop(this)
        } else if (checkMicPermission()) {
            MicrophoneService.start(this)
        } else {
            requestPermissions()
        }
    }

    private fun checkMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        permissionLauncher.launch(permissions)
    }
}

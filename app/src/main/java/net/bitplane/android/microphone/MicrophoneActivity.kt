package net.bitplane.android.microphone

import android.Manifest
import android.content.pm.PackageManager
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

        hasMicPermission = checkMicPermission()
        if (!hasMicPermission && savedInstanceState == null) {
            requestPermissions()
        }

        setContent {
            MicrophoneTheme {
                val active by MicrophoneService.active.collectAsState()

                if (hasMicPermission) {
                    MicrophoneScreen(
                        active = active,
                        onToggle = { toggle(active) },
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

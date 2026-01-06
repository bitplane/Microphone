package net.bitplane.android.microphone

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import net.bitplane.android.microphone.ui.AboutDialog
import net.bitplane.android.microphone.ui.MicrophoneScreen
import net.bitplane.android.microphone.ui.MicrophoneTheme
import net.bitplane.android.microphone.ui.PermissionScreen
import java.io.IOException
import java.io.InputStream

class MicrophoneActivity : ComponentActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var mSharedPreferences: SharedPreferences
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var mActive by mutableStateOf(false)
    private var hasPermissions by mutableStateOf(false)
    private var showPermissionExplanation by mutableStateOf(false)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mSharedPreferences = AppPreferences.prefs(this)
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        mActive = AppPreferences.isActive(mSharedPreferences)
        hasPermissions = refreshPermissionState()

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            hasPermissions = refreshPermissionState()
            val allGranted = results.values.all { it }
            if (allGranted && hasPermissions) {
                showPermissionExplanation = false
                if (mActive) {
                    startService(Intent(this, MicrophoneService::class.java))
                }
            } else {
                showPermissionExplanation = true
            }
        }

        if (mActive && hasPermissions) {
            startService(Intent(this, MicrophoneService::class.java))
        }

        val lastVersion = AppPreferences.lastVersion(mSharedPreferences)
        val thisVersion = getAppVersionCodeCompat()
        val showAboutOnStart = lastVersion != thisVersion
        AppPreferences.setLastVersion(mSharedPreferences, thisVersion)

        val aboutHtml = loadAboutHtml()

        if (!hasPermissions) {
            requestPermissionsFlow()
        }

        setContent {
            MicrophoneTheme {
                var showAbout by rememberSaveable { mutableStateOf(showAboutOnStart) }

                if (hasPermissions) {
                    MicrophoneScreen(
                        active = mActive,
                        onToggle = { toggleActive() },
                        onShowAbout = { showAbout = true },
                    )
                } else {
                    PermissionScreen(
                        showExplanation = showPermissionExplanation,
                        onRequestPermissions = { requestPermissionsFlow() }
                    )
                }

                if (showAbout && hasPermissions) {
                    AboutDialog(
                        aboutHtml = aboutHtml,
                        onDismiss = { showAbout = false }
                    )
                }
            }
        }
    }

    private fun refreshPermissionState(): Boolean =
        hasAudioPermission() && hasNotificationPermission()

    private fun hasAudioPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    private fun getAppVersionCodeCompat(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun loadAboutHtml(): String {
        val inputStream: InputStream = try {
            applicationContext.resources.openRawResource(R.raw.about)
        } catch (_: Exception) {
            return ""
        }

        return try {
            inputStream.bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            ""
        }
    }

    private fun toggleActive() {
        if (!hasPermissions) {
            requestPermissionsFlow()
            return
        }
        AppPreferences.setActive(mSharedPreferences, !mActive)
    }

    private fun requestPermissionsFlow() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        permissionLauncher.launch(permissions)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != null && key == AppPreferences.KEY_ACTIVE) {
            val bActive = sharedPreferences.getBoolean(AppPreferences.KEY_ACTIVE, false)
            val intent = Intent(this, MicrophoneService::class.java)
            if (bActive != mActive) {
                if (bActive && hasPermissions) {
                    startService(intent)
                } else {
                    stopService(intent)
                }
                mActive = bActive
            }
        }
    }
}

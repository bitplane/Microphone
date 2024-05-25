package net.bitplane.android.microphone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {
    private lateinit var buttonRequestPermission: Button
    private lateinit var textViewExplanation: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        buttonRequestPermission = findViewById(R.id.buttonRequestPermission)
        textViewExplanation = findViewById(R.id.textViewExplanation)

        buttonRequestPermission.setOnClickListener { requestPermissions() }

        // Check and request permissions
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val audioPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val notificationPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (audioPermissionGranted && notificationPermissionGranted) {
            // All permissions are granted, proceed to the main activity
            navigateToMainActivity()
        } else {
            // Request missing permissions
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION || requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                // All permissions granted
                navigateToMainActivity()
            } else {
                // Permission denied, show explanation and request button
                showPermissionExplanation()
            }
        }
    }

    private fun showPermissionExplanation() {
        textViewExplanation.visibility = View.VISIBLE
        buttonRequestPermission.visibility = View.VISIBLE
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this@PermissionActivity, MicrophoneActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 200
        private const val REQUEST_NOTIFICATION_PERMISSION = 201
    }
}
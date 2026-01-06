package net.bitplane.android.microphone

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import java.io.IOException
import androidx.core.content.edit

class MicrophoneActivity : AppCompatActivity(), OnSharedPreferenceChangeListener,
    View.OnClickListener {
    private val ABOUT_DIALOG_ID = 0
    private lateinit var mSharedPreferences: SharedPreferences
    private var mActive: Boolean = false

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(APP_TAG, "Opening mic activity")
        mSharedPreferences = getSharedPreferences(APP_TAG, MODE_PRIVATE)
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        mActive = mSharedPreferences.getBoolean("active", false)
        val intent = Intent(this, MicrophoneService::class.java)
        if (mActive) {
            startService(intent)
        }

        setContentView(R.layout.main)
        val b = findViewById<ImageButton>(R.id.RecordButton)
        b.setOnClickListener(this)
        b.setImageDrawable(
            if (mActive) {
                AppCompatResources.getDrawable(this, R.drawable.baseline_mic_24)
            } else {
                AppCompatResources.getDrawable(this, R.drawable.baseline_mic_24_black)
            }
        )

        val lastVersion = mSharedPreferences.getLong("lastVersionLong", -1L)
        val thisVersion = getAppVersionCodeCompat()
        if (lastVersion != thisVersion) {
            mSharedPreferences.edit {
                putLong("lastVersionLong", thisVersion)
            }

            showDialog(ABOUT_DIALOG_ID)
        }
    }

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
        Log.d(APP_TAG, "Closing mic activity")
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        if (item.itemId == R.id.about) {
            showDialog(ABOUT_DIALOG_ID)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    public override fun onCreateDialog(id: Int): Dialog {
        var dialog: Dialog? = null
        if (id == ABOUT_DIALOG_ID) {
            val b = AlertDialog.Builder(this)
            b.setTitle(getString(R.string.about))

            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val aboutView = inflater.inflate(R.layout.about, findViewById(R.id.AboutWebView))

            b.setView(aboutView)

            var data = ""

            val `in` = applicationContext.resources.openRawResource(R.raw.about)
            try {
                var ch: Int
                val buf = StringBuilder()
                while ((`in`.read().also { ch = it }) != -1) {
                    buf.append(ch.toChar())
                }
                data = buf.toString()
            } catch (ignored: IOException) {
            }

            val wv = aboutView.findViewById<WebView>(R.id.AboutWebView)
            wv.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null)

            dialog = b.create()
        }
        return dialog!!
    }

    override fun onClick(v: View) {
        if (v.id == R.id.RecordButton) {
            mSharedPreferences.edit {
                putBoolean("active", !mActive)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != null && key == "active") {
            val bActive = sharedPreferences.getBoolean("active", false)
            val intent = Intent(this, MicrophoneService::class.java)
            if (bActive != mActive) {
                if (bActive) {
                    startService(intent)
                } else {
                    stopService(intent)
                }
                mActive = bActive
                runOnUiThread {
                    val b = findViewById<ImageButton>(R.id.RecordButton)
                    b.setImageDrawable(
                        if (mActive) {
                            AppCompatResources.getDrawable(this, R.drawable.baseline_mic_24)
                        } else {
                            AppCompatResources.getDrawable(this, R.drawable.baseline_mic_24_black)
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val APP_TAG = "Microphone"
    }
}
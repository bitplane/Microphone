package net.bitplane.android.microphone

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AppPreferences {
    const val APP_TAG = "Microphone"
    const val KEY_ACTIVE = "active"
    const val KEY_LAST_VERSION = "lastVersionLong"

    private const val PREFS_NAME = APP_TAG

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isActive(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ACTIVE, false)

    fun setActive(prefs: SharedPreferences, active: Boolean) {
        prefs.edit { putBoolean(KEY_ACTIVE, active) }
    }

    fun setActive(context: Context, active: Boolean) {
        prefs(context).edit { putBoolean(KEY_ACTIVE, active) }
    }

    fun lastVersion(prefs: SharedPreferences): Long =
        prefs.getLong(KEY_LAST_VERSION, -1L)

    fun setLastVersion(prefs: SharedPreferences, version: Long) {
        prefs.edit { putLong(KEY_LAST_VERSION, version) }
    }
}

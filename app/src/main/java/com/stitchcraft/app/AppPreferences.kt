
package com.stitchcraft.app

import android.content.Context

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(
        "stitchcraft_preferences",
        Context.MODE_PRIVATE
    )

    var showSymbols: Boolean
        get() = prefs.getBoolean("show_symbols", true)
        set(value) {
            prefs.edit().putBoolean("show_symbols", value).apply()
        }

    var showColors: Boolean
        get() = prefs.getBoolean("show_colors", true)
        set(value) {
            prefs.edit().putBoolean("show_colors", value).apply()
        }

    var cleanNoise: Boolean
        get() = prefs.getBoolean("clean_noise", true)
        set(value) {
            prefs.edit().putBoolean("clean_noise", value).apply()
        }

    var keepAspectRatio: Boolean
        get() = prefs.getBoolean("keep_aspect_ratio", true)
        set(value) {
            prefs.edit().putBoolean("keep_aspect_ratio", value).apply()
        }

    var lastWidth: Int
        get() = prefs.getInt("last_width", 100)
        set(value) {
            prefs.edit().putInt("last_width", value).apply()
        }

    var lastHeight: Int
        get() = prefs.getInt("last_height", 100)
        set(value) {
            prefs.edit().putInt("last_height", value).apply()
        }

    var lastColors: Int
        get() = prefs.getInt("last_colors", 32)
        set(value) {
            prefs.edit().putInt("last_colors", value).apply()
        }
}

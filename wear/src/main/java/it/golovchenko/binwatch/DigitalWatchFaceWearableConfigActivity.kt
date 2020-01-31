package it.golovchenko.binwatch

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.config.*

class DigitalWatchFaceWearableConfigActivity : Activity() {
    companion object {
        const val PREF = "binconf"
        const val THEMECOLOR = "theme"
        const val BATTERY = "battery"
        const val SECONDS = "seconds"
        const val BCD = "bcd"
        const val HORIZONTAL = "horizontal"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.config)
    }

    fun onTheme(view: View) {
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {

            val theme=when (view.id){
                R.id.theme_red -> "RED"
                R.id.theme_blue -> "BLUE"
                R.id.theme_dark -> "DARK"
                R.id.theme_cyan -> "CYAN"
                R.id.theme_gray -> "GRAY"
                R.id.theme_green -> "GREEN"
                R.id.theme_magenta -> "MAGENTA"
                R.id.theme_pink -> "PINK"
                R.id.theme_yellow -> "YELLOW"
                else -> "WHITE"
            }
            edit().putString(THEMECOLOR, theme).apply()
            Toast.makeText(baseContext, "Theme: $theme", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    fun onBattery(view: View) {
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
            edit().putBoolean(BATTERY, !getBoolean(BATTERY, true)).apply()
            Toast.makeText(baseContext, "Battery: ${if (!getBoolean(BATTERY, false)) "Off" else "On"}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    fun vDirection(view: View) {
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
            edit().putBoolean(HORIZONTAL, !getBoolean(HORIZONTAL, true)).apply()
            Toast.makeText(baseContext, "Horizontal: ${if (!getBoolean(BATTERY, false)) "Off" else "On"}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    fun seconds(view: View) {
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
            edit().putBoolean(SECONDS, !getBoolean(SECONDS, true)).apply()
            Toast.makeText(baseContext, "Seconds: ${if (!getBoolean(SECONDS, false)) "Off" else "On"}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
    fun bcd(view: View) {
        with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
            edit().putBoolean(BCD, !getBoolean(BCD, true)).apply()
            Toast.makeText(baseContext, "BCD: ${if (!getBoolean(BATTERY, false)) "Off" else "On"}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

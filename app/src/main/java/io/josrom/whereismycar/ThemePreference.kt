package io.josrom.whereismycar

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButtonToggleGroup

class ThemePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_theme_toggle
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        val themeToggle = holder.findViewById(R.id.toggle_group_theme) as? MaterialButtonToggleGroup
        
        // Load current theme
        val sharedPrefs = preferenceManager.sharedPreferences
        val currentTheme = sharedPrefs?.getString("theme_selection", "system") ?: "system"
        
        // Set correct button as checked
        themeToggle?.apply {
            when (currentTheme) {
                "light" -> check(R.id.btn_theme_light)
                "dark" -> check(R.id.btn_theme_dark)
                "system" -> check(R.id.btn_theme_system)
            }
        }

        themeToggle?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val themeValue = when (checkedId) {
                    R.id.btn_theme_light -> "light"
                    R.id.btn_theme_dark -> "dark"
                    else -> "system"
                }
                
                // Save preference
                sharedPrefs?.edit {
                    putString("theme_selection", themeValue)
                }
                
                // Apply theme
                applyTheme(themeValue)
            }
        }
    }

    private fun applyTheme(themeValue: String) {
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}

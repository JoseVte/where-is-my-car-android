package io.josrom.whereismycar

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButtonToggleGroup

class LanguagePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_language_toggle
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        val langToggle = holder.findViewById(R.id.toggle_group_language) as? MaterialButtonToggleGroup
        
        // Load current language
        val sharedPrefs = preferenceManager.sharedPreferences
        val currentLang = sharedPrefs?.getString("language_selection", "system") ?: "system"
        
        // Set correct button as checked
        langToggle?.apply {
            when (currentLang) {
                "en" -> check(R.id.btn_lang_en)
                "es" -> check(R.id.btn_lang_es)
                "system" -> check(R.id.btn_lang_system)
            }
        }

        langToggle?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val langValue = when (checkedId) {
                    R.id.btn_lang_en -> "en"
                    R.id.btn_lang_es -> "es"
                    else -> "system"
                }
                
                if (currentLang != langValue) {
                    // Save preference
                    sharedPrefs?.edit {
                        putString("language_selection", langValue)
                    }
                    
                    // Apply language
                    applyLanguage(langValue)
                }
            }
        }
    }

    private fun applyLanguage(langValue: String) {
        val appLocale: LocaleListCompat = if (langValue == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langValue)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}

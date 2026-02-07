package io.josrom.whereismycar

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val syncPreference = findPreference<SwitchPreferenceCompat>("sync")
        syncPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            if (isEnabled) {
                val account = GoogleSignIn.getLastSignedInAccount(requireContext())
                if (account == null) {
                    (activity as? MainActivity)?.startSignIn()
                } else {
                    SyncManager(requireContext()).sync()
                }
            }
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tv = TypedValue()
        if (requireContext().theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            val actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
            view.setPadding(0, actionBarHeight, 0, 0)
        }
    }
}
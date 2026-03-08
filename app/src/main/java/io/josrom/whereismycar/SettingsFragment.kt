package io.josrom.whereismycar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import io.josrom.whereismycar.databinding.FragmentSettingsBinding
import androidx.core.graphics.drawable.toDrawable

class SettingsFragment : PreferenceFragmentCompat() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            updateLocationPreference(true)
        } else {
            updateLocationPreference(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        
        val preferenceView = super.onCreateView(inflater, binding.settingsContainer, savedInstanceState)
        binding.settingsContainer.addView(preferenceView)
        
        return binding.root
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        setupPreferenceListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Remove dividers between preference items
        setDivider(Color.TRANSPARENT.toDrawable())
        setDividerHeight(0)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupPreferenceListeners() {
        findPreference<AppSettingsPreference>("app_settings_card")?.apply {
            onManageCategoriesClick = {
                findNavController().navigate(R.id.action_SettingsFragment_to_ManageCategoriesFragment)
            }
            onArrivalAlertsClick = {
                // Handle arrival alerts click
            }
            onLocationServiceToggle = { isEnabled ->
                if (isEnabled) {
                    checkAndRequestLocationPermission()
                } else {
                    updateLocationPreference(false)
                }
            }
            onDeleteConfirmToggle = { isEnabled ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putBoolean("confirm_deletion", isEnabled)
                }
            }
        }

        findPreference<Preference>("help_support")?.setOnPreferenceClickListener {
            // Handle help support action
            true
        }
    }

    private fun checkAndRequestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                updateLocationPreference(true)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun updateLocationPreference(isEnabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            putBoolean("location_service", isEnabled)
        }
        
        // Refresh the preference to update the switch state
        findPreference<AppSettingsPreference>("app_settings_card")?.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

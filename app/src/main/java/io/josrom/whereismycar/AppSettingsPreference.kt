package io.josrom.whereismycar

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.materialswitch.MaterialSwitch

class AppSettingsPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_app_settings_card
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.isClickable = false

        holder.findViewById(R.id.item_manage_categories)?.setOnClickListener {
            onManageCategoriesClick?.invoke()
        }

        val switchLocation = holder.findViewById(R.id.switch_location) as? MaterialSwitch
        val switchDeleteConfirm = holder.findViewById(R.id.switch_delete_confirm) as? MaterialSwitch
        
        // Initialize state from SharedPreferences without triggering the listener
        val sharedPrefs = preferenceManager.sharedPreferences
        
        val isLocationEnabled = sharedPrefs?.getBoolean("location_service", true) ?: true
        switchLocation?.setOnCheckedChangeListener(null)
        switchLocation?.isChecked = isLocationEnabled
        switchLocation?.setOnCheckedChangeListener { _, isChecked ->
            onLocationServiceToggle?.invoke(isChecked)
        }

        val isDeleteConfirmEnabled = sharedPrefs?.getBoolean("confirm_deletion", true) ?: true
        switchDeleteConfirm?.setOnCheckedChangeListener(null)
        switchDeleteConfirm?.isChecked = isDeleteConfirmEnabled
        switchDeleteConfirm?.setOnCheckedChangeListener { _, isChecked ->
            onDeleteConfirmToggle?.invoke(isChecked)
        }
    }

    fun refresh() {
        notifyChanged()
    }

    var onManageCategoriesClick: (() -> Unit)? = null
    var onArrivalAlertsClick: (() -> Unit)? = null
    var onLocationServiceToggle: ((Boolean) -> Unit)? = null
    var onDeleteConfirmToggle: ((Boolean) -> Unit)? = null
}

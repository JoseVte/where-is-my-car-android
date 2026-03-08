package io.josrom.whereismycar

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton

class SyncPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_sync_card
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val signInBtn = holder.findViewById(R.id.btn_google_signin) as? MaterialButton
        signInBtn?.setOnClickListener {
            (context as? MainActivity)?.startSignIn()
        }
    }
}

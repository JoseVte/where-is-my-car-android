package io.josrom.whereismycar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth

class SyncPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var currentViewHolder: PreferenceViewHolder? = null
    private val authStateListener = FirebaseAuth.AuthStateListener {
        updateViewHolderUI()
    }

    init {
        layoutResource = R.layout.pref_sync_card
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        currentViewHolder = holder
        updateViewHolderUI()
        
        holder.findViewById(R.id.btn_google_signin)?.setOnClickListener {
            (context as? MainActivity)?.startSignIn()
        }

        holder.findViewById(R.id.btn_sign_out_v2)?.setOnClickListener {
            (context as? MainActivity)?.signOut()
        }

        holder.findViewById(R.id.btn_sync_settings)?.setOnClickListener {
            it.findNavController().navigate(R.id.action_SettingsFragment_to_CloudSyncSettingsFragment)
        }
    }

    private fun updateViewHolderUI() {
        val holder = currentViewHolder ?: return
        
        val loggedInContainer = holder.findViewById(R.id.cl_logged_in)
        val loggedOutContainer = holder.findViewById(R.id.cl_logged_out)
        
        val profilePicIv = holder.findViewById(R.id.iv_profile_pic) as? ImageView
        val userNameTv = holder.findViewById(R.id.tv_user_name) as? TextView
        val userEmailTv = holder.findViewById(R.id.tv_user_email) as? TextView
        val lastSyncTv = holder.findViewById(R.id.tv_last_sync) as? TextView
        
        val locationsTv = holder.findViewById(R.id.tv_stats_locations_v2) as? TextView
        val categoriesTv = holder.findViewById(R.id.tv_stats_categories_v2) as? TextView

        @Suppress("DEPRECATION")
        val account = GoogleSignIn.getLastSignedInAccount(context)
        
        if (account != null) {
            loggedInContainer?.visibility = View.VISIBLE
            loggedOutContainer?.visibility = View.GONE
            
            userNameTv?.text = account.displayName ?: context.getString(R.string.app_name)
            userEmailTv?.text = account.email

            // Get last sync time from Firebase
            (context as? MainActivity)?.syncManager?.getLastSyncTime { timestamp ->
                if (timestamp != null) {
                    lastSyncTv?.text = buildString {
                        append(context.getString(R.string.last_sync))
                        append(": ")
                        append(formatRelativeTime(timestamp))
                    }
                    lastSyncTv?.visibility = View.VISIBLE
                } else {
                    lastSyncTv?.text = context.getString(R.string.never_synced)
                    lastSyncTv?.visibility = View.VISIBLE
                }
            }

            if (profilePicIv != null) {
                if (account.photoUrl != null) {
                    Glide.with(context)
                        .load(account.photoUrl)
                        .transform(CircleCrop())
                        .placeholder(R.drawable.ic_search)
                        .into(profilePicIv)
                } else {
                    profilePicIv.setImageResource(R.drawable.ic_search)
                }
            }
            
            (context as? MainActivity)?.syncManager?.getSyncStats(object : SyncManager.SyncStatsListener {
                override fun onStatsLoaded(localLocs: Int, localCats: Int, cloudLocs: Int, cloudCats: Int) {
                    locationsTv?.text = context.getString(R.string.user_stats, localLocs, cloudLocs)
                    categoriesTv?.text = context.getString(R.string.user_stats, localCats, cloudCats)
                }
            })
        } else {
            loggedInContainer?.visibility = View.GONE
            loggedOutContainer?.visibility = View.VISIBLE
        }
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> context.getString(R.string.just_now)
            minutes < 60 -> context.getString(R.string.minutes_ago, minutes)
            hours < 24 -> context.getString(R.string.hours_ago, hours)
            else -> context.getString(R.string.days_ago, days)
        }
    }

    override fun onAttached() {
        super.onAttached()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onDetached() {
        super.onDetached()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }

    fun refresh() {
        notifyChanged()
        updateViewHolderUI()
    }
}

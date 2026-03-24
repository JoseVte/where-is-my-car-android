package io.josrom.whereismycar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import io.josrom.whereismycar.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    lateinit var syncManager: SyncManager

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "signInLauncher result: resultCode=${result.resultCode}, RESULT_OK=$RESULT_OK")
        
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("MainActivity", "✅ Sign in successful: email=${account.email}, displayName=${account.displayName}")
                Log.d("MainActivity", "   idToken=${if (account.idToken != null) "present" else "null"}")
                
                Toast.makeText(this, getString(R.string.signed_in_as, account.email), Toast.LENGTH_SHORT).show()
                
                // Verify immediately that account is saved
                val savedAccount = GoogleSignIn.getLastSignedInAccount(this)
                Log.d("MainActivity", "Immediately after login - getLastSignedInAccount: ${if (savedAccount != null) savedAccount.email else "null"}")
                
                syncManager.sync()
                
                // Trigger multiple refreshes to ensure UI updates
                // First refresh after sync starts
                binding.root.postDelayed({
                    Log.d("MainActivity", "Refresh 1 - Initial (300ms)")
                    refreshSyncPreference()
                }, 300)
                
                // Second refresh after Firebase auth completes
                binding.root.postDelayed({
                    Log.d("MainActivity", "Refresh 2 - Firebase (800ms)")
                    refreshSyncPreference()
                }, 800)
                
                // Third refresh as final confirmation
                binding.root.postDelayed({
                    Log.d("MainActivity", "Refresh 3 - Final (1500ms)")
                    refreshSyncPreference()
                }, 1500)
            } catch (e: ApiException) {
                Log.w("MainActivity", "❌ signInResult failed: code=${e.statusCode}, message=${e.message}")
                Toast.makeText(this, getString(R.string.sign_in_failed, e.message), Toast.LENGTH_LONG).show()
            }
        } else {
            Log.w("MainActivity", "⚠️ Sign in cancelled or failed: resultCode=$result.resultCode")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before super.onCreate
        applySavedTheme()
        
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!sharedPref.getBoolean("onboarding_completed", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = getNavController()
        
        // Setup BottomNavigationView with NavController
        binding.bottomNavigation.setupWithNavController(navController)

        // Add listener for UI updates (like the center button background)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateCenterItemBackground(destination.id)
        }

        // Set click listener for the center explorer button
        binding.bgNavCenterView.setOnClickListener {
            if (navController.currentDestination?.id != R.id.explore_fragment) {
                navController.navigate(R.id.explore_fragment)
            }
        }

        syncManager = SyncManager(this)
        syncManager.sync()
    }

    private fun applySavedTheme() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themeValue = sharedPrefs.getString("theme_selection", "system") ?: "system"
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun updateCenterItemBackground(selectedItemId: Int) {
        val isExploreSelected = selectedItemId == R.id.explore_fragment
        binding.bgNavCenterView.setBackgroundResource(
            if (isExploreSelected) R.drawable.bg_nav_center_selected
            else R.drawable.bg_nav_center_unselected
        )
        
        binding.icNavCenterIcon.alpha = if (isExploreSelected) 1.0f else 0.5f
    }

    fun startSignIn() {
        signInLauncher.launch(syncManager.googleSignInClient.signInIntent)
    }

    fun signOut() {
        syncManager.signOut()
        binding.root.postDelayed({
            refreshSyncPreference()
        }, 500)
        Toast.makeText(this, getString(R.string.signed_out), Toast.LENGTH_SHORT).show()
    }

    private fun refreshSyncPreference() {
        // Try to find and refresh the SyncPreference in SettingsFragment
        supportFragmentManager.fragments.forEach { parentFragment ->
            if (parentFragment is NavHostFragment) {
                parentFragment.childFragmentManager.fragments.forEach { fragment ->
                    if (fragment is SettingsFragment) {
                        Log.d("MainActivity", "Found SettingsFragment, refreshing SyncPreference")
                        // Force refresh to trigger onBindViewHolder update
                        binding.root.postDelayed({
                            fragment.findPreference<SyncPreference>("google_sync_card")?.refresh()
                        }, 100)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-attempt sync when app comes back to foreground (handles token refresh)
        syncManager.sync()
        // Refresh UI to show current login state
        binding.root.postDelayed({
            refreshSyncPreference()
        }, 500)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun getNavController(): NavController {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                getNavController().navigate(R.id.settings_fragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        appBarConfiguration = AppBarConfiguration(navHostFragment.navController.graph)
        return getNavController().navigateUp(appBarConfiguration)
            || super.onSupportNavigateUp()
    }
}

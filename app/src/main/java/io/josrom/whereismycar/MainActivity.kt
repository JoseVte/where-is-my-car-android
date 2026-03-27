package io.josrom.whereismycar

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import io.josrom.whereismycar.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    lateinit var syncManager: SyncManager
    private var isProgrammaticSelection = false

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Toast.makeText(this, getString(R.string.signed_in_as, account.email), Toast.LENGTH_SHORT).show()
                syncManager.sync()
                binding.root.postDelayed({ refreshSyncPreference() }, 300)
                binding.root.postDelayed({ refreshSyncPreference() }, 800)
                binding.root.postDelayed({ refreshSyncPreference() }, 1500)
            } catch (e: ApiException) {
                Toast.makeText(this, getString(R.string.sign_in_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.explore_fragment, R.id.location_history_fragment, R.id.settings_fragment)
        )

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (isProgrammaticSelection) return@setOnItemSelectedListener true
            
            val currentId = navController.currentDestination?.id
            
            if (item.itemId == R.id.explore_fragment) {
                if (currentId != R.id.explore_fragment) {
                    if (!navController.popBackStack(R.id.explore_fragment, false)) {
                        navController.navigate(resId = R.id.explore_fragment, args = null)
                    }
                }
                return@setOnItemSelectedListener true
            }

            if (!navController.popBackStack(item.itemId, false)) {
                navController.navigate(resId = item.itemId, args = null, navOptions = navOptions {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = false 
                })
            }
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener { item ->
            if (isProgrammaticSelection) return@setOnItemReselectedListener
            
            val currentDestId = navController.currentDestination?.id
            if (currentDestId != item.itemId && currentDestId != null) {
                if (!navController.popBackStack(item.itemId, false)) {
                    navController.navigate(resId = item.itemId, args = null, navOptions = navOptions {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = false
                    })
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateCenterItemBackground(destination.id)
            
            val tabToSelect = when (destination.id) {
                R.id.location_history_fragment, R.id.location_details_fragment, R.id.edit_location_fragment -> R.id.location_history_fragment
                R.id.settings_fragment, R.id.manage_categories_fragment, R.id.edit_category_fragment, R.id.cloud_sync_settings_fragment -> R.id.settings_fragment
                R.id.explore_fragment -> R.id.explore_fragment
                else -> null
            }

            tabToSelect?.let { tabId ->
                if (binding.bottomNavigation.selectedItemId != tabId) {
                    isProgrammaticSelection = true
                    binding.bottomNavigation.selectedItemId = tabId
                    isProgrammaticSelection = false
                }
            }
        }

        binding.bgNavCenterView.setOnClickListener {
            if (navController.currentDestination?.id != R.id.explore_fragment) {
                if (!navController.popBackStack(R.id.explore_fragment, false)) {
                    navController.navigate(resId = R.id.explore_fragment, args = null)
                }
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
        binding.root.postDelayed({ refreshSyncPreference() }, 500)
        Toast.makeText(this, getString(R.string.signed_out), Toast.LENGTH_SHORT).show()
    }

    private fun refreshSyncPreference() {
        supportFragmentManager.fragments.forEach { parentFragment ->
            if (parentFragment is NavHostFragment) {
                parentFragment.childFragmentManager.fragments.forEach { fragment ->
                    if (fragment is SettingsFragment) {
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
        syncManager.sync()
        binding.root.postDelayed({ refreshSyncPreference() }, 500)
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
                getNavController().navigate(resId = R.id.settings_fragment, args = null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return getNavController().navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}

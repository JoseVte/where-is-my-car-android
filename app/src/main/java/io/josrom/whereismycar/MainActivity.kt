package io.josrom.whereismycar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import io.josrom.whereismycar.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var syncManager: SyncManager

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
                syncManager.sync()
            } catch (e: ApiException) {
                Log.w("MainActivity", "signInResult:failed code=" + e.statusCode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before super.onCreate
        applySavedTheme()
        
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
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
            if (navController.currentDestination?.id != R.id.ExploreFragment) {
                navController.navigate(R.id.ExploreFragment)
            }
        }

        syncManager = SyncManager(this)
        syncManager.sync()

        // TODO: Only during development, allow enter directly on the first location 
        // on the LocationDetailsFragment, and re-generate on each start.
        if (savedInstanceState == null && BuildConfig.DEBUG) {
            lifecycleScope.launch {
                val repository = SavedLocationRepository(this@MainActivity)
                withContext(Dispatchers.IO) {
                    val locations = repository.getAllLocations()
                    if (locations.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val bundle = Bundle().apply {
                                putInt("locationId", locations.random().id)
                            }
                            navController.navigate(R.id.LocationDetailsFragment, bundle)
                        }
                    }
                }
            }
        }
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
        val isExploreSelected = selectedItemId == R.id.ExploreFragment
        binding.bgNavCenterView.setBackgroundResource(
            if (isExploreSelected) R.drawable.bg_nav_center_selected
            else R.drawable.bg_nav_center_unselected
        )
        
        binding.icNavCenterIcon.alpha = if (isExploreSelected) 1.0f else 0.5f
    }

    fun startSignIn() {
        signInLauncher.launch(syncManager.googleSignInClient.signInIntent)
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
                getNavController().navigate(R.id.SettingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return getNavController().navigateUp(appBarConfiguration)
            || super.onSupportNavigateUp()
    }
}

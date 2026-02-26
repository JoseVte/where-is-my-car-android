package io.josrom.whereismycar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import io.josrom.whereismycar.databinding.ActivityMainBinding

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
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!sharedPref.getBoolean("onboarding_completed", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, ExploreFragment())
                .commit()
            binding.bottomNavigation.selectedItemId = R.id.nav_explore
            updateCenterItemBackground(R.id.nav_explore)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_history -> HistoryFragment()
                R.id.nav_explore -> ExploreFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> ExploreFragment()
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit()

            updateCenterItemBackground(item.itemId)
            true
        }

        syncManager = SyncManager(this)
        syncManager.sync()
    }

    private fun updateCenterItemBackground(selectedItemId: Int) {
        val isExploreSelected = selectedItemId == R.id.nav_explore
        binding.bgNavCenterView.setBackgroundResource(
            if (isExploreSelected) R.drawable.bg_nav_center_selected
            else R.drawable.bg_nav_center_unselected
        )
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

    fun goToExplorer(view: View) {
        getNavController().navigate(R.id.ExploreFragment)
    }
}

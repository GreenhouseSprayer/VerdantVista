package Verdant.Vista

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import Verdant.Vista.databinding.ActivityMainBinding
import Verdant.Vista.ui.SharedTaxonViewModel
import Verdant.Vista.util.AppConstants

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedViewModel: SharedTaxonViewModel
    private lateinit var appUpdateManager: AppUpdateManager
    
    private val updateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            showUpdateDownloadedSnackbar()
        }
    }

    companion object {
        private const val UPDATE_REQUEST_CODE = 123
        private const val TAG = "MainActivity"
        private const val DAYS_FOR_FLEXIBLE_UPDATE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val theme = prefs.getString(AppConstants.KEY_THEME, AppConstants.THEME_FOREST)
        when (theme) {
            AppConstants.THEME_DESERT -> setTheme(R.style.Theme_VerdantVista_Desert)
            AppConstants.THEME_MIDNIGHT -> setTheme(R.style.Theme_VerdantVista_Midnight)
            AppConstants.THEME_OCEAN -> setTheme(R.style.Theme_VerdantVista_Ocean)
            AppConstants.THEME_SPRING -> setTheme(R.style.Theme_VerdantVista_Spring)
            AppConstants.THEME_SUMMER -> setTheme(R.style.Theme_VerdantVista_Summer)
            AppConstants.THEME_AUTUMN -> setTheme(R.style.Theme_VerdantVista_Autumn)
            AppConstants.THEME_WINTER -> setTheme(R.style.Theme_VerdantVista_Winter)
            else -> setTheme(R.style.Theme_VerdantVista_Forest)
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController
        val navView: BottomNavigationView = binding.bottomNavView
        
        sharedViewModel = ViewModelProvider(this).get(SharedTaxonViewModel::class.java)

        navView.setupWithNavController(navController)
        
        // Handle Refresh Button in Bottom Nav
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> {
                    // Navigate to discover first if we aren't there
                    if (navController.currentDestination?.id != R.id.nav_discover) {
                        navController.navigate(R.id.nav_discover)
                    }
                    sharedViewModel.requestRefresh()
                    false // Don't select the refresh item
                }
                R.id.nav_discover -> {
                    if (navController.currentDestination?.id != R.id.nav_discover) {
                        navController.navigate(R.id.nav_discover)
                    }
                    true
                }
                R.id.nav_favorites -> {
                    if (navController.currentDestination?.id != R.id.nav_favorites) {
                        navController.navigate(R.id.nav_favorites)
                    }
                    true
                }
                else -> false
            }
        }

        // Custom tinting to keep Refresh Green
        navView.itemIconTintList = createColorStateList()
        navView.itemTextColor = createColorStateList()

        handleIntent(intent)
        initInAppUpdate()
    }

    private fun createColorStateList(): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        
        // We get the primary color from the current theme for the "checked" state
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data

        val colors = intArrayOf(
            primaryColor,
            Color.parseColor("#888888") // Default inactive color
        )
        return ColorStateList(states, colors)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "VIEW_DETAILS") {
            val taxonId = intent.getIntExtra("taxon_id", -1)
            if (taxonId != -1) {
                sharedViewModel.requestTaxonId(taxonId)
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
                val navController = navHostFragment.navController
                navController.navigate(R.id.nav_discover)
                binding.bottomNavView.selectedItemId = R.id.nav_discover
            }
        }
    }

    private fun initInAppUpdate() {
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(updateListener)

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            val isStale = (appUpdateInfo.clientVersionStalenessDays() ?: 0) >= DAYS_FOR_FLEXIBLE_UPDATE
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                && (isStale || appUpdateInfo.updatePriority() >= 4)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.FLEXIBLE,
                        this,
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start update flow", e)
                }
            }
        }
    }

    private fun showUpdateDownloadedSnackbar() {
        Snackbar.make(
            binding.root,
            "Update ready to install",
            Snackbar.LENGTH_LONG
        ).apply {
            setAction("RESTART") { appUpdateManager.completeUpdate() }
            show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    showUpdateDownloadedSnackbar()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(updateListener)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UPDATE_REQUEST_CODE && resultCode != RESULT_OK) {
            Log.d(TAG, "Update flow cancelled or failed.")
        }
    }
}

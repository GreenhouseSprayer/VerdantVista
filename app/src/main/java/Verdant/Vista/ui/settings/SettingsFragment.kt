package Verdant.Vista.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import Verdant.Vista.ui.widget.ProximityNotificationWorker
import com.google.android.gms.location.LocationServices
import java.util.Locale
import Verdant.Vista.R
import kotlinx.coroutines.launch
import Verdant.Vista.databinding.FragmentSettingsBinding
import Verdant.Vista.ui.widget.MemoryWidget
import Verdant.Vista.util.AppConstants

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            // Revert to Worldwide if permission denied
            val prefs = requireContext().getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(AppConstants.KEY_LOCATION_INDEX, 1).apply() // Index 1 is Worldwide now
            prefs.edit().putInt(AppConstants.KEY_PLACE_ID, -1).apply()
            
            val locations = resources.getStringArray(R.array.locations_array)
            binding.locationSelector.setText(locations[1], false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val prefs = requireContext().getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString("last_lat", location.latitude.toString()).apply()
                    prefs.edit().putString("last_lng", location.longitude.toString()).apply()
                    Log.d("SettingsFragment", "Location updated: ${location.latitude}, ${location.longitude}")

                    // --- Auto-State Detection ---
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        val stateName = addresses?.firstOrNull()?.adminArea
                        if (stateName != null) {
                            Log.d("SettingsFragment", "Detected state: $stateName")
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsFragment", "Reverse geocoding failed", e)
                    }
                } else {
                    Log.w("SettingsFragment", "Location is null")
                }
            }.addOnFailureListener { e ->
                Log.e("SettingsFragment", "Failed to get location", e)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        setupSettings()
        setupSupportButtons()
        return binding.root
    }

    private fun setupSettings() {
        val prefs = requireContext().getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // --- ALL Categories (RESTORED FULL LOGIC) ---
        val savedCategories = prefs.getStringSet(AppConstants.KEY_TAXON_TYPES, setOf(AppConstants.TAXON_PLANTS)) ?: setOf(AppConstants.TAXON_PLANTS)
        
        val categoryCheckboxes = listOf(
            binding.checkPlants to AppConstants.TAXON_PLANTS,
            binding.checkBirds to AppConstants.TAXON_BIRDS,
            binding.checkInsects to AppConstants.TAXON_INSECTS,
            binding.checkFungi to AppConstants.TAXON_FUNGI,
            binding.checkMammals to AppConstants.TAXON_MAMMALS,
            binding.checkHerps to AppConstants.TAXON_HERPS,
            binding.checkAquatic to AppConstants.TAXON_AQUATIC
        )

        categoryCheckboxes.forEach { (checkbox, type) ->
            checkbox.isChecked = savedCategories.contains(type)
            checkbox.setOnClickListener {
                val selected = categoryCheckboxes.filter { it.first.isChecked }.map { it.second }.toSet()
                if (selected.isEmpty()) {
                    checkbox.isChecked = true // Force at least one selection
                } else {
                    prefs.edit().putStringSet(AppConstants.KEY_TAXON_TYPES, selected).apply()
                }
            }
        }

        // --- Theme Dropdown (Themed Rounded Items) ---
        val themes = resources.getStringArray(R.array.themes_array)
        val themeAdapter = UnfilteredArrayAdapter(requireContext(), R.layout.item_dropdown, themes)
        binding.themeSelector.setAdapter(themeAdapter)

        binding.themeSelector.setOnClickListener {
            binding.themeSelector.showDropDown()
        }

        val savedTheme = prefs.getString(AppConstants.KEY_THEME, AppConstants.THEME_FOREST)
        val themeValues = listOf(
            AppConstants.THEME_FOREST, AppConstants.THEME_DESERT, AppConstants.THEME_MIDNIGHT,
            AppConstants.THEME_OCEAN, AppConstants.THEME_SPRING, AppConstants.THEME_SUMMER,
            AppConstants.THEME_AUTUMN, AppConstants.THEME_WINTER
        )
        val currentThemeIndex = themeValues.indexOf(savedTheme).coerceAtLeast(0)
        binding.themeSelector.setText(themes[currentThemeIndex], false)

        binding.themeSelector.setOnItemClickListener { _, _, position, _ ->
            if (position in themeValues.indices) {
                val newTheme = themeValues[position]
                if (newTheme != savedTheme) {
                    prefs.edit().putString(AppConstants.KEY_THEME, newTheme).apply()
                    activity?.recreate()
                }
            }
        }

        // --- Location Dropdown (Themed Rounded Items) ---
        val locations = resources.getStringArray(R.array.locations_array)
        val locationIds = resources.getStringArray(R.array.location_ids)
        val locationAdapter = UnfilteredArrayAdapter(requireContext(), R.layout.item_dropdown, locations)
        binding.locationSelector.setAdapter(locationAdapter)

        binding.locationSelector.setOnClickListener {
            binding.locationSelector.showDropDown()
        }

        val savedLocationIndex = prefs.getInt(AppConstants.KEY_LOCATION_INDEX, 0)
        if (savedLocationIndex in locations.indices) {
            binding.locationSelector.setText(locations[savedLocationIndex], false)
        }

        binding.locationSelector.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putInt(AppConstants.KEY_LOCATION_INDEX, position).apply()
            val placeIdString = locationIds[position]
            val placeId = placeIdString.toIntOrNull() ?: -1
            prefs.edit().putInt(AppConstants.KEY_PLACE_ID, placeId).apply()
            
            if (placeId == 0) { // Local
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                } else {
                    fetchCurrentLocation()
                }
            }
        }

        // --- Interval Dropdown (Themed Rounded Items) ---
        val intervals = arrayOf("15 Minutes", "30 Minutes", "1 Hour")
        val intervalValues = listOf(15L, 30L, 60L)
        val intervalAdapter = UnfilteredArrayAdapter(requireContext(), R.layout.item_dropdown, intervals)
        binding.intervalSelector.setAdapter(intervalAdapter)

        binding.intervalSelector.setOnClickListener {
            binding.intervalSelector.showDropDown()
        }

        val savedInterval = prefs.getLong(AppConstants.KEY_UPDATE_INTERVAL, 60L)
        val currentIntervalIndex = intervalValues.indexOf(savedInterval).coerceAtLeast(0)
        binding.intervalSelector.setText(intervals[currentIntervalIndex], false)

        binding.intervalSelector.setOnItemClickListener { _, _, position, _ ->
            if (position in intervalValues.indices) {
                val newInterval = intervalValues[position]
                prefs.edit().putLong(AppConstants.KEY_UPDATE_INTERVAL, newInterval).apply()
                MemoryWidget.scheduleNextUpdate(requireContext().applicationContext)
            }
        }

        // --- Proximity Notifications ---
        binding.switchNotifications.isChecked = prefs.getBoolean(AppConstants.KEY_PROXIMITY_ENABLED, false)
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(AppConstants.KEY_PROXIMITY_ENABLED, isChecked) }
            if (isChecked) {
                // Request Notification permission for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
                ProximityNotificationWorker.schedule(requireContext().applicationContext)
            } else {
                ProximityNotificationWorker.cancel(requireContext().applicationContext)
            }
        }
    }

    private fun setupSupportButtons() {
        binding.btnHelp.setOnClickListener {
            AlertDialog.Builder(ContextThemeWrapper(requireContext(), R.style.Theme_VerdantVista_Dialog))
                .setTitle("Widget Setup")
                .setMessage("Long-press your Home Screen > Widgets > Verdant Vista.\n\nThe widget will update automatically based on these settings!")
                .setPositiveButton("Got it", null)
                .show()
        }

        binding.btnPrivacy.setOnClickListener {
            AlertDialog.Builder(ContextThemeWrapper(requireContext(), R.style.Theme_VerdantVista_Dialog))
                .setTitle("Privacy First")
                .setMessage("Verdant Vista collects zero personal data. Your scouting history and collection stay on your device.")
                .setPositiveButton("Close", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

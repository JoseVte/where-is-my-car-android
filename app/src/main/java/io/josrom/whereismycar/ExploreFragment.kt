package io.josrom.whereismycar

import android.Manifest
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import io.josrom.whereismycar.databinding.FragmentExploreBinding
import io.josrom.whereismycar.databinding.FragmentLocationListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.IconOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class ExploreFragment : Fragment(), LocationListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var _db: DBHelper
    private lateinit var _mapController: IMapController

    private var _binding: FragmentExploreBinding? = null

    private var _locationUpdateMinTime: Long = 0
    private var _locationUpdateMinDistance: Float = 0.0f

    private var _location: Location? = null

    private var _firstLoad: Boolean = true

    private var _myLocationNewOverlay: MyLocationNewOverlay? = null

    private var _compassOverlay: CompassOverlay? = null

    private var _currentCarOverlay: IconOverlay? = null

    private lateinit var _prefs: SharedPreferences

    private var _lastMapCenterLat: Double? = null
    private var _lastMapCenterLon: Double? = null
    private var _lastZoomLevel: Double = 20.0

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        _prefs.registerOnSharedPreferenceChangeListener(this)

        // Restore state if available
        savedInstanceState?.let {
            if (it.containsKey("last_lat")) {
                _lastMapCenterLat = it.getDouble("last_lat")
                _lastMapCenterLon = it.getDouble("last_lon")
                _firstLoad = it.getBoolean("first_load", true)
            }
            _lastZoomLevel = it.getDouble("last_zoom", 20.0)
        }

        // 1. Initialize OSMDroid configuration before inflating the layout
        getInstance().load(context, _prefs)

        _binding = FragmentExploreBinding.inflate(inflater, container, false)

        // 2. Setup the map
        val map = _binding?.map
        if (map != null) {
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setMultiTouchControls(true)

            _mapController = map.controller

            // Apply restored center and zoom
            _lastMapCenterLat?.let { lat ->
                _lastMapCenterLon?.let { lon ->
                    _mapController.setCenter(GeoPoint(lat, lon))
                    _mapController.setZoom(_lastZoomLevel)
                }
            } ?: run {
                _mapController.setZoom(20.0)
            }

            // Initialize the overlay properly
            _myLocationNewOverlay =
                MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)

            _compassOverlay = CompassOverlay(requireContext(), map)
            _compassOverlay?.setCompassCenter(36.0F, 140.0F)

            map.overlays.add(_myLocationNewOverlay)
            map.overlays.add(_compassOverlay)

            updateLocationServiceStatus()
        }

        // Create database helper instance
        _db = DBHelper(requireContext(), null)

        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Clear the activity title for this fragment
        (activity as? AppCompatActivity)?.supportActionBar?.title = ""

        binding?.btnSavePosition?.setOnClickListener {
            // Save the current location to database
            val location = _currentCarOverlay?.position

            if (location != null) {
                lifecycleScope.launch {
                    val address = withContext(Dispatchers.IO) {
                        try {
                            GeocoderNominatim(requireContext().packageName)
                                .getFromLocation(location.latitude, location.longitude, 1)
                        } catch (e: Exception) {
                            Log.e("LocationListFragment", "Error getting address", e)
                            null
                        }
                    }

                    if (!address.isNullOrEmpty()) {
                        _db.insertLocation(address.first(), location)
                    } else {
                        _db.insertLocation("Unknown location", location)
                    }
                    getSavedLocations()
                }
            }
        }

        binding?.btnCenterLocation?.setOnClickListener {
            _location?.let {
                val centerPoint = GeoPoint(it.latitude, it.longitude)
                _mapController.animateTo(centerPoint)
            }
        }

        // Initialize RecyclerView
//        binding?.carLocations?.layoutManager = LinearLayoutManager(context)

//        this.getSavedLocations()
    }

    private fun getSavedLocations() {
//        binding?.carLocations?.adapter = LocationAdapter(_db.getAllLocations(20), { id ->
//            val bundle = Bundle().apply {
//                putInt("locationId", id)
//            }
//            findNavController().navigate(
//                R.id.action_LocationListFragment_to_LocationDetailsFragment,
//                bundle
//            )
//        }, { id ->
//            _db.deleteLocation(id)
//            getSavedLocations()
//        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _prefs.unregisterOnSharedPreferenceChangeListener(this)
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title = ""
        _binding?.map?.onResume()
        updateLocationServiceStatus()
        this.getSavedLocations()
    }

    override fun onPause() {
        super.onPause()
        _binding?.map?.let { map ->
            _lastMapCenterLat = map.mapCenter.latitude
            _lastMapCenterLon = map.mapCenter.longitude
            _lastZoomLevel = map.zoomLevelDouble
        }
        _compassOverlay?.disableCompass()
        _myLocationNewOverlay?.disableMyLocation()
        unregisterLocationListener()
        _binding?.map?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _lastMapCenterLat?.let { outState.putDouble("last_lat", it) }
        _lastMapCenterLon?.let { outState.putDouble("last_lon", it) }
        outState.putDouble("last_zoom", _lastZoomLevel)
        outState.putBoolean("first_load", _firstLoad)
    }

    override fun onLocationChanged(location: Location) {
        if (location.provider == null)
            return;

        _location = location;

        if (_firstLoad) {
            val startPoint = GeoPoint(location.latitude, location.longitude)
            _mapController.setCenter(startPoint)
            _currentCarOverlay = IconOverlay(
                startPoint,
                ResourcesCompat.getDrawable(resources, R.drawable.car, null)
            )
            _binding?.map?.overlays?.add(_currentCarOverlay)
            _firstLoad = false
        }
    }

    fun checkPermission(context: Context, activity: MainActivity): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true;
        } else {
            requestPermissions(activity)
            return false
        }
    }

    fun requestPermissions(activity: MainActivity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            1
        )
    }


    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateLocationServiceStatus()
            }
        }
    }

    private fun updateLocationServiceStatus() {
        val isEnabled = _prefs.getBoolean("location_service", true)
        if (isEnabled) {
            if (checkPermission(requireContext(), activity as MainActivity)) {
                _myLocationNewOverlay?.enableMyLocation()
                _compassOverlay?.enableCompass()
                registerLocationListener()
                binding?.btnCenterLocation?.visibility = View.VISIBLE
            } else {
                binding?.btnCenterLocation?.visibility = View.GONE
            }
        } else {
            _myLocationNewOverlay?.disableMyLocation()
            _compassOverlay?.disableCompass()
            unregisterLocationListener()
            binding?.btnCenterLocation?.visibility = View.GONE
        }
    }

    private fun registerLocationListener() {
        val mLocationManager: LocationManager =
            context?.getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = mLocationManager.getProviders(true)
        for (provider in providers) {
            try {
                mLocationManager.requestLocationUpdates(
                    provider,
                    _locationUpdateMinTime,
                    _locationUpdateMinDistance,
                    this
                )
                val location = mLocationManager.getLastKnownLocation(provider)
                if (location != null) {
                    onLocationChanged(location)
                    break
                }
            } catch (e: SecurityException) {
            }
        }
    }

    private fun unregisterLocationListener() {
        val mLocationManager: LocationManager =
            context?.getSystemService(LOCATION_SERVICE) as LocationManager
        mLocationManager.removeUpdates(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "location_service") {
            updateLocationServiceStatus()
        }
    }
}
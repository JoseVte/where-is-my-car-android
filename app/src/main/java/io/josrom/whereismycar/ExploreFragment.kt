package io.josrom.whereismycar

import android.Manifest
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.josrom.whereismycar.databinding.FragmentExploreBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.IconOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class ExploreFragment : Fragment(), LocationListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var _locationRepository: SavedLocationRepository
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
    private lateinit var _bottomSheetBehavior: BottomSheetBehavior<View>

    private var _lastMapCenterLat: Double? = null
    private var _lastMapCenterLon: Double? = null
    private var _lastZoomLevel: Double = 20.0

    private var _isLoading = false
    private var _offset = 0
    private val _limit = 10
    private var _hasMoreData = true
    private var _locationAdapter: LocationAdapter? = null
    private var _searchQuery: String? = null

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

            _myLocationNewOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)
            _compassOverlay = CompassOverlay(requireContext(), map)
            _compassOverlay?.setCompassCenter(36.0F, 140.0F)

            map.overlays.add(_myLocationNewOverlay)
            map.overlays.add(_compassOverlay)

            updateLocationServiceStatus()
        }

        // Initialize repository
        _locationRepository = SavedLocationRepository(requireContext())
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Clear the activity title for this fragment
        (activity as? AppCompatActivity)?.supportActionBar?.title = ""

        _bottomSheetBehavior = BottomSheetBehavior.from(binding!!.bottomSheet)
        _bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED ||
                    newState == BottomSheetBehavior.STATE_COLLAPSED ||
                    newState == BottomSheetBehavior.STATE_EXPANDED) {
                    _location?.let {
                        _mapController.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val height = bottomSheet.height
                val peekHeight = _bottomSheetBehavior.peekHeight
                val coveredHeight = if (slideOffset >= 0) {
                    peekHeight + (height - peekHeight) * slideOffset
                } else {
                    peekHeight * (1 + slideOffset)
                }

                // Allow the map to extend slightly under the rounded corners of the bottom sheet
                val cornerOverlap = (144 * resources.displayMetrics.density).toInt()
                val params = binding?.map?.layoutParams as? ViewGroup.MarginLayoutParams
                params?.let {
                    it.bottomMargin = (coveredHeight.toInt() - cornerOverlap).coerceAtLeast(0)
                    binding?.map?.layoutParams = it
                }
            }
        })

        // Initial map adjustment to match the collapsed bottom sheet
        binding?.map?.post {
            val params = binding?.map?.layoutParams as? ViewGroup.MarginLayoutParams
            params?.let {
                val cornerOverlap = (144 * resources.displayMetrics.density).toInt()
                it.bottomMargin = (_bottomSheetBehavior.peekHeight - cornerOverlap).coerceAtLeast(0)
                binding?.map?.layoutParams = it
            }
        }

        binding?.btnSavePosition?.setOnClickListener {
            // Save the current location to database
            val location = _currentCarOverlay?.position ?: _location?.let { GeoPoint(it.latitude, it.longitude) }
            if (location != null) {
                lifecycleScope.launch {
                    val address = withContext(Dispatchers.IO) {
                        try {
                            GeocoderNominatim(requireContext().packageName)
                                .getFromLocation(location.latitude, location.longitude, 1)
                        } catch (e: Exception) {
                            Log.e("ExploreFragment", "Error getting address", e)
                            null
                        }
                    }

                    if (!address.isNullOrEmpty()) {
                        _locationRepository.insertLocation(address.first(), location)
                    } else {
                        _locationRepository.insertLocation("Unknown location", location)
                    }
                    resetAndLoadLocations()
                }
            }
        }

        binding?.btnCenterLocation?.setOnClickListener {
            _location?.let {
                val centerPoint = GeoPoint(it.latitude, it.longitude)
                _mapController.animateTo(centerPoint)
            }
        }

        setupSearchBar()
        setupRecyclerView()
        loadMoreLocations()
    }

    private fun setupSearchBar() {
        binding?.etSearch?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                _bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        binding?.etSearch?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v)
                _bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                binding?.etSearch?.clearFocus()
                true
            } else {
                false
            }
        }

        binding?.etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                _searchQuery = s?.toString()
                _locationAdapter?.setSearchQuery(_searchQuery ?: "")
                resetAndLoadLocations()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupRecyclerView() {
        _locationAdapter = LocationAdapter(mutableListOf(), { id ->
            val bundle = Bundle().apply { putInt("locationId", id) }
            findNavController().navigate(R.id.action_ExploreFragment_to_LocationDetailsFragment, bundle)
        }, { id ->
            val bundle = Bundle().apply { putInt("locationId", id) }
            findNavController().navigate(R.id.action_ExploreFragment_to_LocationDetailsFragment, bundle)
        })

        binding?.carLocations?.apply {
            val layoutManager = LinearLayoutManager(context)
            this.layoutManager = layoutManager
            this.adapter = _locationAdapter

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy > 0) { // Scrolling down
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                        if (!_isLoading && _hasMoreData) {
                            if ((visibleItemCount + pastVisibleItems) >= totalItemCount) {
                                loadMoreLocations()
                            }
                        }
                    }
                }
            })
        }
    }

    private fun resetAndLoadLocations() {
        _offset = 0
        _hasMoreData = true
        _isLoading = false
        loadMoreLocations(reset = true)
    }

    private fun loadMoreLocations(reset: Boolean = false) {
        if (_isLoading || (!_hasMoreData && !reset)) return
        _isLoading = true

        lifecycleScope.launch(Dispatchers.IO) {
            val locations = _locationRepository.getAllLocations(_limit, _offset, _searchQuery)
            withContext(Dispatchers.Main) {
                if (locations.isEmpty() && !reset) {
                    _hasMoreData = false
                } else {
                    if (reset) {
                        _locationAdapter?.clearAndAdd(locations)
                    } else {
                        _locationAdapter?.addLocations(locations)
                    }
                    _offset += locations.size
                    if (locations.size < _limit) {
                        _hasMoreData = false
                    }
                }
                _isLoading = false
            }
        }
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
        resetAndLoadLocations()
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
        if (location.provider == null) return
        _location = location
        if (_firstLoad) {
            val startPoint = GeoPoint(location.latitude, location.longitude)
            _mapController.setCenter(startPoint)
            _currentCarOverlay = IconOverlay(startPoint, ResourcesCompat.getDrawable(resources, R.drawable.car, null))
            _binding?.map?.overlays?.add(_currentCarOverlay)
            _firstLoad = false
        }
    }

    fun checkPermission(context: Context, activity: MainActivity): Boolean {
        return if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            requestPermissions(activity)
            false
        }
    }

    fun requestPermissions(activity: MainActivity) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
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
        val mLocationManager: LocationManager = context?.getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = mLocationManager.getProviders(true)
        for (provider in providers) {
            try {
                mLocationManager.requestLocationUpdates(provider, _locationUpdateMinTime, _locationUpdateMinDistance, this)
                val location = mLocationManager.getLastKnownLocation(provider)
                if (location != null) {
                    onLocationChanged(location)
                    break
                }
            } catch (e: SecurityException) {}
        }
    }

    private fun unregisterLocationListener() {
        val mLocationManager: LocationManager = context?.getSystemService(LOCATION_SERVICE) as LocationManager
        mLocationManager.removeUpdates(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "location_service") {
            updateLocationServiceStatus()
        }
    }
}

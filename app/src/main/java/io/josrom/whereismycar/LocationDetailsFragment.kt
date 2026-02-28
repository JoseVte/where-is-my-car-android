package io.josrom.whereismycar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import io.josrom.whereismycar.databinding.FragmentLocationDetailsBinding
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import kotlin.math.abs
import androidx.core.graphics.toColorInt

class LocationDetailsFragment : Fragment() {

    private var _binding: FragmentLocationDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var _locationRepository: SavedLocationRepository
    private lateinit var _categoryRepository: CategoryRepository
    private var locationId: Int = -1
    private var location: SavedLocation? = null
    
    private var locationOverlay: MyLocationNewOverlay? = null
    private var roadOverlay: Polyline? = null
    private var isRouteShown = false
    private var savedZoomLevel = 20.0
    private var transportMode = OSRMRoadManager.MEAN_BY_FOOT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            locationId = it.getInt("locationId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize osmdroid configuration
        val ctx = requireContext()
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        // Crucial: Set a unique User-Agent for tile servers
        Configuration.getInstance().userAgentValue = ctx.packageName
        
        _binding = FragmentLocationDetailsBinding.inflate(inflater, container, false)
        _locationRepository = SavedLocationRepository(ctx)
        _categoryRepository = CategoryRepository(ctx)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadLocationData()
        setupClickListeners()
        setupTransportToggle()
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        checkLocationSettings()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    private fun checkLocationSettings() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        binding.btnStartNavigation.isEnabled = isGpsEnabled || isNetworkEnabled
    }

    private fun loadLocationData() {
        location = _locationRepository.getLocation(locationId)
        location?.let { loc ->
            binding.locationNameTop.text = loc.displayName.ifEmpty { getString(R.string.app_name) }
            binding.locationName.text = loc.displayName.ifEmpty { getString(R.string.saved_location_default) }
            binding.addressSubtext.text = loc.address.ifEmpty { getString(R.string.address_format, loc.city, loc.country) }
            binding.noteText.text = loc.note?.ifEmpty { getString(R.string.no_notes_added) } ?: getString(R.string.no_notes_added)
            binding.dateText.text = loc.date

            if (loc.categoryId == null) {
                binding.categoryText.visibility = View.GONE
            } else {
                val category = _categoryRepository.getCategory(loc.categoryId)
                if (category != null) {
                    binding.categoryText.visibility = View.VISIBLE
                    if (category.label.startsWith("@string/")) {
                        binding.categoryText.text = getString(category.label.substringAfterLast("/").toInt())
                    } else {
                        binding.categoryText.text = category.label
                    }

                    if (category.icon.startsWith("@drawable/")) {
                        binding.categoryIconEmoji.visibility = View.GONE
                        binding.categoryIconDrawable.visibility = View.VISIBLE
                        val resourceName = category.icon.substringAfterLast("/")
                        val resourceId = resources.getIdentifier(resourceName, "drawable", requireContext().packageName)
                        if (resourceId != 0) {
                            val drawable = ResourcesCompat.getDrawable(resources, resourceId, requireContext().theme)
                            binding.categoryIconDrawable.setImageDrawable(drawable)
                        }
                    } else {
                        binding.categoryIconEmoji.visibility = View.VISIBLE
                        binding.categoryIconDrawable.visibility = View.GONE
                        binding.categoryIconEmoji.text = category.icon
                        binding.categoryIconEmoji.setTextColor(category.color.toColorInt())
                    }
                } else {
                    binding.categoryText.visibility = View.GONE
                }
            }

            val lat = String.format(Locale.US, "%.4f", abs(loc.latitude))
            val lon = String.format(Locale.US, "%.4f", abs(loc.longitude))
            val latDir = if (loc.latitude >= 0) "N" else "S"
            val lonDir = if (loc.longitude >= 0) "E" else "W"
            binding.coordsText.text = getString(R.string.coords_format, lat, latDir, lon, lonDir)

            val map = binding.map
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setMultiTouchControls(true)
            map.maxZoomLevel = 20.0
            val mapController = map.controller
            mapController.setZoom(20.0)
            val startPoint = GeoPoint(loc.latitude, loc.longitude)
            mapController.setCenter(startPoint)

            val marker = Marker(map)
            marker.position = startPoint
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = loc.displayName
            map.overlays.add(marker)
            
            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)
            locationOverlay?.enableMyLocation()
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Coordinates", binding.coordsText.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, getString(R.string.coords_copied), Toast.LENGTH_SHORT).show()
        }

        binding.btnStartNavigation.setOnClickListener {
            if (isRouteShown) removeRoute() else showRoute()
        }
        
        binding.btnEdit.setOnClickListener {
            Toast.makeText(context, getString(R.string.edit_coming_soon), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTransportToggle() {
        binding.transportToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = when (checkedId) {
                    R.id.btn_walk -> OSRMRoadManager.MEAN_BY_FOOT
                    R.id.btn_drive -> OSRMRoadManager.MEAN_BY_CAR
                    else -> OSRMRoadManager.MEAN_BY_FOOT
                }
                if (transportMode != newMode) {
                    transportMode = newMode
                    if (isRouteShown) {
                        showRoute()
                    }
                }
            }
        }
    }

    private fun showRoute() {
        val map = binding.map
        val destPoint = GeoPoint(location!!.latitude, location!!.longitude)
        
        val myLocation = locationOverlay?.myLocation
        if (myLocation == null) {
            Toast.makeText(context, getString(R.string.waiting_for_gps), Toast.LENGTH_SHORT).show()
            locationOverlay?.enableMyLocation()
            return
        }

        if (!map.overlays.contains(locationOverlay)) {
            map.overlays.add(locationOverlay)
        }

        savedZoomLevel = map.zoomLevelDouble

        // Show loading state
        binding.progressNavigation.visibility = View.VISIBLE
        binding.btnStartNavigation.text = ""
        binding.btnStartNavigation.icon = null
        binding.btnStartNavigation.isEnabled = false

        Thread {
            val roadManager: RoadManager = OSRMRoadManager(requireContext(), Configuration.getInstance().userAgentValue)
            (roadManager as OSRMRoadManager).setMean(transportMode)
            
            val waypoints = ArrayList<GeoPoint>()
            waypoints.add(myLocation)
            waypoints.add(destPoint)

            val road = roadManager.getRoad(waypoints)
            
            Handler(Looper.getMainLooper()).post {
                // Hide loading state
                binding.progressNavigation.visibility = View.GONE
                binding.btnStartNavigation.isEnabled = true
                binding.btnStartNavigation.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_navigation, requireContext().theme)

                if (road.mStatus == Road.STATUS_OK) {
                    if (roadOverlay != null) {
                        map.overlays.remove(roadOverlay)
                    }
                    
                    roadOverlay = RoadManager.buildRoadOverlay(road)
                    map.overlays.add(roadOverlay)
                    map.zoomToBoundingBox(road.mBoundingBox, true, 350)
                    map.invalidate()
                    isRouteShown = true
                    
                    binding.btnStartNavigation.text = getString(R.string.stop_navigation)
                    
                    if (road.mLength < 1.0) {
                        val meters = (road.mLength * 1000).toInt()
                        binding.distanceText.text = getString(R.string.distance_m_away, meters)
                    } else {
                        binding.distanceText.text = getString(R.string.distance_km_away, road.mLength)
                    }
                    
                    val totalMinutes = (road.mDuration / 60).toInt()
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60

                    val timeString = if (hours > 0) {
                        if (transportMode == OSRMRoadManager.MEAN_BY_FOOT) {
                            getString(R.string.time_hr_min_walking, hours, minutes)
                        } else {
                            getString(R.string.time_hr_min_driving, hours, minutes)
                        }
                    } else {
                        if (transportMode == OSRMRoadManager.MEAN_BY_FOOT) {
                            getString(R.string.time_min_walking, minutes)
                        } else {
                            getString(R.string.time_min_driving, minutes)
                        }
                    }
                    binding.timeText.text = timeString
                    
                    binding.distanceText.visibility = View.VISIBLE
                    binding.timeText.visibility = View.VISIBLE
                    binding.transportToggleGroup.visibility = View.VISIBLE

                } else {
                    binding.btnStartNavigation.text = getString(R.string.start_navigation)
                    Toast.makeText(context, "Error loading route", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun removeRoute() {
        val map = binding.map
        if (roadOverlay != null) {
            map.overlays.remove(roadOverlay)
            roadOverlay = null
        }
        if (locationOverlay != null) {
            map.overlays.remove(locationOverlay)
        }
        map.invalidate()
        isRouteShown = false
        
        binding.btnStartNavigation.text = getString(R.string.start_navigation)
        binding.distanceText.visibility = View.GONE
        binding.timeText.visibility = View.GONE
        binding.transportToggleGroup.visibility = View.GONE

        location?.let {
            map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            map.controller.setZoom(savedZoomLevel)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationOverlay?.disableMyLocation()
        _binding = null
    }
}

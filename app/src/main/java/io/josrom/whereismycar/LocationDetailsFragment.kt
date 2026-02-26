package io.josrom.whereismycar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
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

class LocationDetailsFragment : Fragment() {

    private var _binding: FragmentLocationDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var _locationRepository: SavedLocationRepository
    private var locationId: Int = -1
    private var location: SavedLocation? = null
    
    private var locationOverlay: MyLocationNewOverlay? = null
    private var roadOverlay: Polyline? = null
    private var isRouteShown = false

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadLocationData()
        setupClickListeners()
        
        binding.distanceText.visibility = View.GONE
        binding.timeText.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume() // Important for tiles
        checkLocationSettings()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause() // Important for tiles
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
            binding.locationName.text = loc.displayName.ifEmpty { "Saved Location" }
            binding.addressSubtext.text = "${loc.city}, ${loc.country}"
            binding.noteText.text = loc.note?.ifEmpty { "No notes added for this location." } ?: "No notes added for this location."
            binding.dateText.text = loc.date
            binding.categoryText.text = "Leisure" 

            val lat = String.format(Locale.US, "%.4f", abs(loc.latitude))
            val lon = String.format(Locale.US, "%.4f", abs(loc.longitude))
            val latDir = if (loc.latitude >= 0) "N" else "S"
            val lonDir = if (loc.longitude >= 0) "E" else "W"
            binding.coordsText.text = "$lat° $latDir, $lon° $lonDir"

            val map = binding.map
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setMultiTouchControls(true)
            val mapController = map.controller
            mapController.setZoom(18.0)
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
            Toast.makeText(context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnStartNavigation.setOnClickListener {
            if (isRouteShown) removeRoute() else showRoute()
        }
        
        binding.btnEdit.setOnClickListener {
            Toast.makeText(context, "Edit functionality coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnLayers.setOnClickListener {
            showMapLayersMenu(it)
        }
    }

    private fun showMapLayersMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, "Standard Map")
        popup.menu.add(0, 2, 0, "Public Transport")
        popup.menu.add(0, 3, 0, "Topographic")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> binding.map.setTileSource(TileSourceFactory.MAPNIK)
                2 -> binding.map.setTileSource(TileSourceFactory.PUBLIC_TRANSPORT)
                3 -> binding.map.setTileSource(TileSourceFactory.OpenTopo)
            }
            binding.map.invalidate()
            true
        }
        popup.show()
    }

    private fun showRoute() {
        val map = binding.map
        val destPoint = GeoPoint(location!!.latitude, location!!.longitude)
        
        val myLocation = locationOverlay?.myLocation
        if (myLocation == null) {
            Toast.makeText(context, "Waiting for GPS location...", Toast.LENGTH_SHORT).show()
            locationOverlay?.enableMyLocation()
            return
        }

        if (!map.overlays.contains(locationOverlay)) {
            map.overlays.add(locationOverlay)
        }

        Thread {
            val roadManager: RoadManager = OSRMRoadManager(requireContext(), Configuration.getInstance().userAgentValue)
            val waypoints = ArrayList<GeoPoint>()
            waypoints.add(myLocation)
            waypoints.add(destPoint)
            
            val road = roadManager.getRoad(waypoints)
            
            Handler(Looper.getMainLooper()).post {
                if (road.mStatus == Road.STATUS_OK) {
                    roadOverlay = RoadManager.buildRoadOverlay(road)
                    map.overlays.add(roadOverlay)
                    map.zoomToBoundingBox(road.mBoundingBox, true, 150)
                    map.invalidate()
                    isRouteShown = true
                    
                    binding.btnStartNavigation.text = "Stop Navigation"
                    binding.distanceText.text = String.format(Locale.US, "%.1f km away", road.mLength)
                    binding.timeText.text = String.format(Locale.US, "%d min driving", (road.mDuration / 60).toInt())
                    
                    binding.distanceText.visibility = View.VISIBLE
                    binding.timeText.visibility = View.VISIBLE
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
        
        binding.btnStartNavigation.text = "Start Navigation"
        binding.distanceText.visibility = View.GONE
        binding.timeText.visibility = View.GONE

        location?.let {
            map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            map.controller.setZoom(18.0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationOverlay?.disableMyLocation()
        _binding = null
    }
}

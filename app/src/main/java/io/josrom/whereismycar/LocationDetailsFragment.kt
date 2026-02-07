package io.josrom.whereismycar

import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
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

class LocationDetailsFragment : Fragment() {

    private var _binding: FragmentLocationDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var _db: DBHelper
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
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(requireContext()))
        _binding = FragmentLocationDetailsBinding.inflate(inflater, container, false)
        _db = DBHelper(requireContext(), null)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadLocationData()
        setupRouteButton()
    }

    override fun onResume() {
        super.onResume()
        checkLocationSettings()
    }

    private fun checkLocationSettings() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        binding.btnRoute.visibility = if (isGpsEnabled || isNetworkEnabled) View.VISIBLE else View.GONE
    }

    private fun loadLocationData() {
        location = _db.getLocation(locationId)
        if (location != null) {
            // Set the date as the activity title
            (activity as? AppCompatActivity)?.supportActionBar?.title = location!!.date

            binding.dateLocation.text = location!!.date
            val addressText = location!!.address.filter { (key, value) ->  key.startsWith("Address Line") && value.isNotEmpty() }
            val otherAddressText = location!!.address.filter { (key, value) ->  !key.startsWith("Address Line") && value.isNotEmpty() }
            binding.addressText.text = addressText.values.joinToString("\n")
            binding.otherAddressText.text = otherAddressText.map { (key, value) -> "$key: $value" }.joinToString("\n")
            binding.latitude.text = location!!.latitude.toString()
            binding.longitude.text = location!!.longitude.toString()

            val map = binding.map
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setMultiTouchControls(true)
            val mapController = map.controller
            mapController.setZoom(18.0)
            val startPoint = GeoPoint(location!!.latitude, location!!.longitude)
            mapController.setCenter(startPoint)

            val marker = Marker(map)
            marker.position = startPoint
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = "Car Location"
            map.overlays.add(marker)
            
            // Initialize location overlay but don't enable it yet
            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)
            locationOverlay?.enableMyLocation()
        }
    }

    private fun setupRouteButton() {
        binding.btnRoute.setOnClickListener {
            if (isRouteShown) {
                removeRoute()
            } else {
                showRoute()
            }
        }
    }

    private fun showRoute() {
        val map = binding.map
        val destPoint = GeoPoint(location!!.latitude, location!!.longitude)
        
        val myLocation = locationOverlay?.myLocation
        if (myLocation == null) {
            // If location is not yet available, try to enable it and wait a bit or show a message
            locationOverlay?.enableMyLocation()
            // For now, we just return if not available
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
                    
                    // Zoom out to show the whole route
                    map.zoomToBoundingBox(road.mBoundingBox, true, 150)
                    
                    map.invalidate()
                    isRouteShown = true
                    binding.btnRoute.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
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
        binding.btnRoute.setImageResource(android.R.drawable.ic_menu_directions)
        
        // Return to car location
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

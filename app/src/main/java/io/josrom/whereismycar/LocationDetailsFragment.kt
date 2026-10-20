package io.josrom.whereismycar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.josrom.whereismycar.databinding.FragmentLocationDetailsBinding
import io.josrom.whereismycar.databinding.LayoutHeaderBinding
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.bonuspack.routing.RoadNode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.IOrientationConsumer
import org.osmdroid.views.overlay.compass.IOrientationProvider
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

class LocationDetailsFragment : Fragment(), IOrientationConsumer {

    private var _binding: FragmentLocationDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var headerBinding: LayoutHeaderBinding
    private lateinit var _locationRepository: SavedLocationRepository
    private lateinit var _categoryRepository: CategoryRepository
    private var locationId: Int = -1
    private var location: SavedLocation? = null
    
    private var locationOverlay: MyLocationNewOverlay? = null
    private var compassOverlay: CompassOverlay? = null
    private var orientationProvider: InternalCompassOrientationProvider? = null
    private var roadOverlay: Polyline? = null
    private val stepMarkers = mutableListOf<Marker>()
    private var currentRoad: Road? = null
    private var isRouteShown = false
    private var isNavigating = false
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
        val ctx = requireContext()
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = ctx.packageName
        
        _binding = FragmentLocationDetailsBinding.inflate(inflater, container, false)
        headerBinding = LayoutHeaderBinding.bind(binding.layoutHeader.root)
        _locationRepository = SavedLocationRepository(ctx)
        _categoryRepository = CategoryRepository(ctx)
        (activity as? MainActivity)?.let { mainActivity ->
            _locationRepository.setSyncManager(mainActivity.syncManager)
            _categoryRepository.setSyncManager(mainActivity.syncManager)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        loadLocationData()
        setupClickListeners()
        setupTransportToggle()
        setupStepsRecycler()
    }

    private fun setupHeader() {
        headerBinding.tvToolbarTitle.text = getString(R.string.app_name)
        headerBinding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        checkLocationSettings()
        if (isNavigating) {
            orientationProvider?.startOrientationProvider(this)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        orientationProvider?.stopOrientationProvider()
    }

    private fun checkLocationSettings() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        binding.btnStartNavigation.isEnabled = isGpsEnabled || isNetworkEnabled
    }

    private fun loadLocationData() {
        location = _locationRepository.getLocation(locationId, true)
        location?.let { loc ->
            headerBinding.tvToolbarTitle.text = loc.displayName.ifEmpty { getString(R.string.app_name) }
            binding.locationName.text = loc.displayName.ifEmpty { getString(R.string.saved_location_default) }
            binding.addressSubtext.text = loc.address.ifEmpty { getString(R.string.address_format, loc.city, loc.country) }
            binding.noteText.text = loc.note?.ifEmpty { getString(R.string.no_notes_added) } ?: getString(R.string.no_notes_added)
            binding.dateText.text = loc.formattedDate

            if (loc.categoryId == null) {
                binding.categoryText.text = getString(R.string.uncategorized)
                val virtualUncategorized = Category(icon = "ic_forbidden", color = "#7D6E5D")
                virtualUncategorized.setupIcon(binding.categoryIconDrawable, binding.categoryIconEmoji)
            } else {
                val category = _categoryRepository.getCategory(loc.categoryId)
                if (category != null) {
                    binding.categoryText.text = category.getResolvedLabel(requireContext())
                    category.setupIcon(binding.categoryIconDrawable, binding.categoryIconEmoji)
                } else {
                    binding.categoryText.text = getString(R.string.uncategorized)
                    val virtualUncategorized = Category(icon = "ic_forbidden", color = "#7D6E5D")
                    virtualUncategorized.setupIcon(binding.categoryIconDrawable, binding.categoryIconEmoji)
                }
            }

            binding.coordsText.text = loc.formattedCoordinates(requireContext())

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
            map.overlays.add(locationOverlay)

            compassOverlay = CompassOverlay(requireContext(), InternalCompassOrientationProvider(requireContext()), map)
            compassOverlay?.setCompassCenter(30f, 30f)
            map.overlays.add(compassOverlay)
        }
    }

    private fun setupClickListeners() {
        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Coordinates", binding.coordsText.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, getString(R.string.coords_copied), Toast.LENGTH_SHORT).show()
        }

        binding.btnStartNavigation.setOnClickListener {
            when {
                !isRouteShown -> showRoute()
                !isNavigating -> startNavigation()
                else -> stopNavigation()
            }
        }
        
        binding.btnRemoveRoute.setOnClickListener {
            removeRoute()
        }
        
        binding.btnEdit.setOnClickListener {
            val bundle = Bundle().apply {
                putInt("locationId", locationId)
            }
            findNavController().navigate(resId = R.id.action_location_details_fragment_to_edit_location_fragment, args = bundle)
        }

        binding.btnShare.setOnClickListener {
            location?.share(requireContext())
        }
        
        binding.centerLocation.setOnClickListener {
            locationOverlay?.myLocation?.let {
                binding.map.controller.animateTo(it)
            }
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

    private fun setupStepsRecycler() {
        binding.stepsRecycler.layoutManager = LinearLayoutManager(context)
        binding.stepsRecycler.adapter = NavigationStepAdapter(emptyList())
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

        savedZoomLevel = map.zoomLevelDouble

        // Show loading state
        binding.progressNavigation.visibility = View.VISIBLE
        binding.btnStartNavigation.text = ""
        binding.btnStartNavigation.icon = null
        binding.btnStartNavigation.isEnabled = false
        binding.btnRemoveRoute.visibility = View.GONE
        binding.stepsContainer.visibility = View.GONE

        Thread {
            val roadManager: RoadManager = OSRMRoadManager(requireContext(), Configuration.getInstance().userAgentValue)
            (roadManager as OSRMRoadManager).setMean(transportMode)
            
            val waypoints = ArrayList<GeoPoint>()
            waypoints.add(myLocation)
            waypoints.add(destPoint)

            val road = roadManager.getRoad(waypoints)
            currentRoad = road
            
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
                    
                    // Clear previous step markers
                    stepMarkers.forEach { map.overlays.remove(it) }
                    stepMarkers.clear()
                    
                    // Add numbered markers for each step node
                    for (i in road.mNodes.indices) {
                        val node = road.mNodes[i]
                        val nodeMarker = Marker(map)
                        nodeMarker.position = node.mLocation
                        nodeMarker.icon = createStepIcon(i + 1)
                        nodeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        nodeMarker.title = "Step ${i + 1}"
                        nodeMarker.snippet = node.mInstructions
                        map.overlays.add(nodeMarker)
                        stepMarkers.add(nodeMarker)
                    }
                    
                    map.zoomToBoundingBox(road.mBoundingBox, true, 350)
                    map.invalidate()
                    isRouteShown = true
                    isNavigating = false
                    
                    binding.btnStartNavigation.text = getString(R.string.start_navigation)
                    binding.btnRemoveRoute.visibility = View.VISIBLE
                    binding.stepsContainer.visibility = View.VISIBLE
                    
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

                    // Prepare steps
                    (binding.stepsRecycler.adapter as? NavigationStepAdapter)?.updateSteps(road.mNodes)

                } else {
                    binding.btnStartNavigation.text = getString(R.string.calculate_route)
                    binding.btnRemoveRoute.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.error_loading_route), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun createStepIcon(number: Int): BitmapDrawable {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt() // 32dp
        val bitmap = createBitmap(size, size)
        bitmap.applyCanvas {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = ContextCompat.getColor(requireContext(), R.color.primary)
            drawCircle(size / 2f, size / 2f, size / 2f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 16 * density // 16sp approx
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            
            val text = number.toString()
            val textOffset = (paint.descent() + paint.ascent()) / 2f
            drawText(text, size / 2f, size / 2f - textOffset, paint)
        }
        return bitmap.toDrawable(resources)
    }

    private fun startNavigation() {
        isNavigating = true
        binding.btnStartNavigation.text = getString(R.string.stop_navigation)
        locationOverlay?.enableFollowLocation()
        binding.map.controller.setZoom(18.0)
        binding.stepsContainer.visibility = View.VISIBLE
        
        // Start orientation provider for heading-up map
        orientationProvider = InternalCompassOrientationProvider(requireContext())
        orientationProvider?.startOrientationProvider(this)
        
        Toast.makeText(context, getString(R.string.navigation_started), Toast.LENGTH_SHORT).show()
    }

    override fun onOrientationChanged(orientation: Float, source: IOrientationProvider?) {
        if (isNavigating) {
            // Heading-up: Map orientation is opposite of device orientation
            binding.map.mapOrientation = -orientation
            binding.map.invalidate()
        }
    }

    private fun stopNavigation() {
        isNavigating = false
        binding.btnStartNavigation.text = getString(R.string.start_navigation)
        locationOverlay?.disableFollowLocation()
        binding.stepsContainer.visibility = View.GONE
        
        // Remove step markers from map
        stepMarkers.forEach { binding.map.overlays.remove(it) }
        stepMarkers.clear()

        // Reset orientation
        binding.map.mapOrientation = 0f
        orientationProvider?.stopOrientationProvider()
    }

    private fun removeRoute() {
        val map = binding.map
        if (roadOverlay != null) {
            map.overlays.remove(roadOverlay)
            roadOverlay = null
        }
        
        // Remove step markers
        stepMarkers.forEach { map.overlays.remove(it) }
        stepMarkers.clear()
        
        if (locationOverlay != null) {
            locationOverlay?.disableFollowLocation()
        }

        map.mapOrientation = 0f
        orientationProvider?.stopOrientationProvider()
        map.invalidate()
        isRouteShown = false
        isNavigating = false
        currentRoad = null
        
        binding.btnStartNavigation.text = getString(R.string.calculate_route)
        binding.btnRemoveRoute.visibility = View.GONE
        binding.distanceText.visibility = View.GONE
        binding.timeText.visibility = View.GONE
        binding.transportToggleGroup.visibility = View.GONE
        binding.stepsContainer.visibility = View.GONE

        location?.let {
            map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            map.controller.setZoom(savedZoomLevel)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationOverlay?.disableMyLocation()
        locationOverlay?.disableFollowLocation()
        compassOverlay?.disableCompass()
        orientationProvider?.stopOrientationProvider()
        _binding = null
    }

    private inner class NavigationStepAdapter(private var steps: List<RoadNode>) :
        RecyclerView.Adapter<NavigationStepAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.step_icon)
            val instruction: TextView = view.findViewById(R.id.step_instruction)
            val distance: TextView = view.findViewById(R.id.step_distance)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_navigation_step, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val step = steps[position]
            holder.instruction.text = step.mInstructions ?: getString(R.string.step_continue)
            
            if (step.mLength < 1.0) {
                holder.distance.text = getString(R.string.distance_m_short, (step.mLength * 1000).toInt())
            } else {
                holder.distance.text = String.format(Locale.getDefault(), getString(R.string.distance_km_short), step.mLength)
            }
            
            // Set numbered icon
            holder.icon.setImageDrawable(createStepIcon(position + 1))
            
            holder.itemView.setOnClickListener {
                binding.map.controller.animateTo(step.mLocation)
                if (position < stepMarkers.size) {
                    stepMarkers[position].showInfoWindow()
                }
            }
        }

        override fun getItemCount() = steps.size

        fun updateSteps(newSteps: List<RoadNode>) {
            val oldSize = steps.size
            steps = newSteps
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize)
            }
            notifyItemRangeInserted(0, steps.size)
        }
    }
}

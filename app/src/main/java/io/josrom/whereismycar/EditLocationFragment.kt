package io.josrom.whereismycar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.josrom.whereismycar.databinding.FragmentEditLocationBinding
import io.josrom.whereismycar.databinding.LayoutHeaderBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class EditLocationFragment : Fragment() {

    private var _binding: FragmentEditLocationBinding? = null
    private val binding get() = _binding!!
    private lateinit var headerBinding: LayoutHeaderBinding
    private lateinit var locationRepository: SavedLocationRepository
    private lateinit var categoryRepository: CategoryRepository
    private var locationId: Int = -1
    private var currentLocation: SavedLocation? = null
    private var selectedCategoryId: Int? = null

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
        Configuration.getInstance().load(ctx, androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx))
        // Crucial: Set a unique User-Agent for tile servers
        Configuration.getInstance().userAgentValue = ctx.packageName
        
        _binding = FragmentEditLocationBinding.inflate(inflater, container, false)
        headerBinding = LayoutHeaderBinding.bind(binding.layoutHeader.root)
        locationRepository = SavedLocationRepository(ctx)
        categoryRepository = CategoryRepository(ctx)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        loadLocationData()
        setupClickListeners()
        setupFocusListeners()
    }

    private fun setupHeader() {
        headerBinding.tvToolbarTitle.text = getString(R.string.edit_location)
        headerBinding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadLocationData() {
        currentLocation = locationRepository.getLocation(locationId, true)
        currentLocation?.let { loc ->
            binding.etLocationDisplayName.setText(loc.displayName)
            binding.etNotes.setText(loc.note)
            selectedCategoryId = loc.categoryId

            binding.tvCoordinates.text = loc.formattedCoordinates(requireContext())
            binding.locationAddress.text = loc.address.ifEmpty { getString(R.string.address_format, loc.city, loc.country) }

            setupMap(loc)
            loadCategories()
        }
    }

    private fun setupMap(loc: SavedLocation) {
        val map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(false)
        map.controller.setZoom(18.0)
        val startPoint = GeoPoint(loc.latitude, loc.longitude)
        map.controller.setCenter(startPoint)

        val marker = Marker(map)
        marker.position = startPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun loadCategories() {
        val categories = categoryRepository.getAllCategories()
        binding.categoryChipGroup.removeAllViews()

        // None / Uncategorized chip
        val isNoneSelected = selectedCategoryId == null
        val noneChip = createFilterChip(
            context = requireContext(),
            label = getString(R.string.uncategorized),
            iconRes = R.drawable.ic_forbidden,
            isSelected = isNoneSelected,
            baseColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        ) {
            selectedCategoryId = null
            loadCategories()
        }
        binding.categoryChipGroup.addView(noneChip)

        for (category in categories) {
            val isSelected = category.id == selectedCategoryId
            val chip = category.createChip(requireContext(), isSelected) {
                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                loadCategories() // Refresh all chips to update visuals
            }
            binding.categoryChipGroup.addView(chip)
        }
    }

    private fun setupClickListeners() {
        binding.btnCopyCoords.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.coordinates), binding.tvCoordinates.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, getString(R.string.coords_copied), Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        binding.btnDeleteLocation.setOnClickListener {
            confirmDelete()
        }
    }

    private fun setupFocusListeners() {
        // Automatically scroll to the EditText when it gains focus
        binding.etNotes.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.editScrollView.postDelayed({
                    binding.editScrollView.smoothScrollTo(0, binding.notesLabel.top)
                }, 100)
            }
        }
        
        binding.etLocationDisplayName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.editScrollView.postDelayed({
                    binding.editScrollView.smoothScrollTo(0, binding.displayNameLabel.top)
                }, 100)
            }
        }
    }

    private fun saveChanges() {
        val newName = binding.etLocationDisplayName.text.toString()
        val newNotes = binding.etNotes.text.toString()
        
        locationRepository.updateLocation(locationId, newName, selectedCategoryId, newNotes)
        Toast.makeText(requireContext(), getString(R.string.location_updated), Toast.LENGTH_SHORT).show()
        findNavController().popBackStack()
    }

    private fun confirmDelete() {
        currentLocation?.confirmAndDelete(requireContext(), locationRepository) {
            Toast.makeText(requireContext(), getString(R.string.location_deleted), Toast.LENGTH_SHORT).show()
            findNavController().popBackStack() // Back to details
            findNavController().popBackStack() // Back to list
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

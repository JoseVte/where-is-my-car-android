package io.josrom.whereismycar

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import io.josrom.whereismycar.databinding.FragmentHistoryBinding
import io.josrom.whereismycar.databinding.LayoutHeaderBinding
import org.osmdroid.config.Configuration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var headerBinding: LayoutHeaderBinding
    private lateinit var adapter: LocationAdapter
    private lateinit var repository: SavedLocationRepository
    private lateinit var categoryRepository: CategoryRepository

    private val selectedCategoryIds = mutableSetOf<Int>()
    private var startDate: String? = null
    private var endDate: String? = null
    private var activePreset: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        headerBinding = LayoutHeaderBinding.bind(binding.layoutHeader.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Configuration.getInstance().userAgentValue = requireContext().packageName
        repository = SavedLocationRepository(requireContext())
        categoryRepository = CategoryRepository(requireContext())
        (activity as? MainActivity)?.let { mainActivity ->
            repository.setSyncManager(mainActivity.syncManager)
            categoryRepository.setSyncManager(mainActivity.syncManager)
        }

        setupHeader()
        setupRecyclerView()
        setupListeners()
        renderFilters()
        loadLocations()
    }

    private fun setupHeader() {
        headerBinding.tvToolbarTitle.text = getString(R.string.history)
        headerBinding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        adapter = LocationAdapter(
            locations = mutableListOf(),
            onItemClick = { location ->
                val bundle = Bundle().apply {
                    putInt("locationId", location.id)
                }
                findNavController().navigate(R.id.action_location_history_fragment_to_location_details_fragment, bundle)
            },
            onEditClick = { location ->
                val bundle = Bundle().apply {
                    putInt("locationId", location.id)
                }
                findNavController().navigate(R.id.action_location_history_fragment_to_edit_location_fragment, bundle)
            },
            onShareClick = { location ->
                location.share(requireContext())
            },
            onDeleteClick = { location ->
                location.confirmAndDelete(requireContext(), repository) {
                    loadLocations()
                    Toast.makeText(context, getString(R.string.location_deleted), Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.recyclerViewLocations.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewLocations.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnFilters.setOnClickListener {
            val transition = AutoTransition()
            transition.duration = 150
            TransitionManager.beginDelayedTransition(binding.root, transition)
            binding.filtersWrapper.isVisible = !binding.filtersWrapper.isVisible
            updateActiveFiltersVisibility()
        }

        binding.editTextSearch.addTextChangedListener {
            loadLocations()
        }
    }

    private fun renderFilters() {
        renderDatePresets()
        renderCategories()
        renderActiveFilters()
    }

    private fun renderDatePresets() {
        binding.dateFiltersContainer.removeAllViews()

        // "All" preset - only show if a filter is active
        if (activePreset != null) {
            val chip = createFilterChip(
                context = requireContext(),
                label = getString(R.string.all),
                iconRes = R.drawable.ic_time,
                isSelected = false,
                baseColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            ) {
                startDate = null
                endDate = null
                activePreset = null
                renderFilters()
                loadLocations()
            }
            binding.dateFiltersContainer.addView(chip)
        }

        // "Today" preset
        val todayStr = getString(R.string.today)
        addPresetChip(todayStr, activePreset == todayStr) {
            val today = getFormattedDate(0)
            startDate = today
            endDate = today
            activePreset = todayStr
            renderFilters()
            loadLocations()
        }

        // "Yesterday" preset
        val yesterdayStr = getString(R.string.yesterday)
        addPresetChip(yesterdayStr, activePreset == yesterdayStr) {
            val yesterday = getFormattedDate(-1)
            startDate = yesterday
            endDate = yesterday
            activePreset = yesterdayStr
            renderFilters()
            loadLocations()
        }

        // "Last 7 Days" preset
        val last7DaysStr = getString(R.string.last_7_days)
        addPresetChip(last7DaysStr, activePreset == last7DaysStr) {
            startDate = getFormattedDate(-7)
            endDate = getFormattedDate(0)
            activePreset = last7DaysStr
            renderFilters()
            loadLocations()
        }

        // "Custom Range" button
        val customStr = getString(R.string.custom)
        val isCustom = activePreset == customStr
        val customLabel = if (isCustom && startDate != null && endDate != null) {
            getFormattedDateRange(startDate!!, endDate!!)
        } else {
            "$customStr..."
        }

        addPresetChip(customLabel, isCustom) {
            showDateRangePicker()
        }
    }

    private fun addPresetChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val defaultTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val color = if (isSelected) primaryColor else defaultTextColor
        val chip = createFilterChip(
            context = requireContext(),
            label = label,
            iconRes = R.drawable.ic_time,
            isSelected = isSelected,
            baseColor = color,
            onClick = onClick
        )
        binding.dateFiltersContainer.addView(chip)
    }

    private fun renderCategories() {
        binding.categoriesContainer.removeAllViews()

        // "All" button - only show if a filter is active
        if (selectedCategoryIds.isNotEmpty()) {
            val allChip = createFilterChip(
                context = requireContext(),
                label = getString(R.string.all),
                iconRes = R.drawable.ic_map,
                isSelected = false,
                baseColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            ) {
                selectedCategoryIds.clear()
                renderFilters()
                loadLocations()
            }
            binding.categoriesContainer.addView(allChip)
        }

        // Uncategorized filter
        val isUncategorizedSelected = selectedCategoryIds.contains(-1)
        val uncategorizedChip = createFilterChip(
            context = requireContext(),
            label = getString(R.string.uncategorized),
            iconRes = 0, // Consistently hide forbidden/uncategorized icon foreground
            isSelected = isUncategorizedSelected,
            baseColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        ) {
            if (isUncategorizedSelected) {
                selectedCategoryIds.remove(-1)
            } else {
                selectedCategoryIds.add(-1)
            }
            renderFilters()
            loadLocations()
        }
        binding.categoriesContainer.addView(uncategorizedChip)

        val categories = categoryRepository.getAllCategories()
        for (category in categories) {
            val isSelected = selectedCategoryIds.contains(category.id)
            val chip = category.createChip(requireContext(), isSelected, category.label == getString(R.string.uncategorized)) {
                if (selectedCategoryIds.contains(category.id)) {
                    selectedCategoryIds.remove(category.id)
                } else {
                    selectedCategoryIds.add(category.id)
                }
                renderFilters()
                loadLocations()
            }
            binding.categoriesContainer.addView(chip)
        }
    }

    private fun renderActiveFilters() {
        binding.activeFiltersGroup.removeAllViews()

        // Date filter
        activePreset?.let { preset ->
            val label = if (preset == getString(R.string.custom)) getFormattedDateRange(startDate!!, endDate!!) else preset
            val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
            val chip = createActiveFilterChip(label, primaryColor, R.drawable.ic_time) {
                activePreset = null
                startDate = null
                endDate = null
                renderFilters()
                loadLocations()
            }
            binding.activeFiltersGroup.addView(chip)
        }

        // Uncategorized filter
        if (selectedCategoryIds.contains(-1)) {
            val color = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            val chip = createActiveFilterChip(getString(R.string.uncategorized), color, 0) {
                selectedCategoryIds.remove(-1)
                renderFilters()
                loadLocations()
            }
            binding.activeFiltersGroup.addView(chip)
        }

        // Category filters
        val categories = categoryRepository.getAllCategories()
        selectedCategoryIds.forEach { id ->
            if (id == -1) return@forEach
            val category = categories.find { it.id == id }
            category?.let {
                val categoryColor = it.getResolvedColor(requireContext())
                val isForbidden = it.icon == "ic_forbidden" || it.icon == "@drawable/ic_forbidden"
                val iconRes = if (isForbidden) 0 else {
                    val resourceName = if (it.icon.startsWith("@drawable/")) it.icon.substringAfterLast("/") else it.icon
                    resources.getIdentifier(resourceName, "drawable", requireContext().packageName)
                }
                
                val chip = createActiveFilterChip(it.getResolvedLabel(requireContext()), categoryColor, if (iconRes != 0) iconRes else 0) {
                    selectedCategoryIds.remove(id)
                    renderFilters()
                    loadLocations()
                }
                binding.activeFiltersGroup.addView(chip)
            }
        }

        updateActiveFiltersVisibility()
    }

    private fun createActiveFilterChip(label: String, color: Int, iconRes: Int, onRemove: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            text = label
            if (iconRes != 0) {
                setChipIconResource(iconRes)
                chipIconTint = android.content.res.ColorStateList.valueOf(color)
            } else {
                isChipIconVisible = false
            }
            isCloseIconVisible = true
            setOnCloseIconClickListener { onRemove() }
            
            val alphaBackground = androidx.core.graphics.ColorUtils.setAlphaComponent(color, (255 * 0.2).toInt())
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(alphaBackground)
            setTextColor(color)
            closeIconTint = android.content.res.ColorStateList.valueOf(color)
            chipStrokeColor = android.content.res.ColorStateList.valueOf(color)
            chipStrokeWidth = (1 * resources.displayMetrics.density)

            setEnsureMinTouchTargetSize(false)
        }
    }

    private fun updateActiveFiltersVisibility() {
        val hasActiveFilters = activePreset != null || selectedCategoryIds.isNotEmpty()
        binding.activeFiltersScroll.isVisible = hasActiveFilters && !binding.filtersWrapper.isVisible
    }

    private fun getFormattedDate(daysOffset: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysOffset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    private fun getFormattedDateRange(start: String, end: String): String {
        val sYear = start.substring(0, 4)
        val sMonth = start.substring(5, 7)
        val sDay = start.substring(8, 10)

        val eYear = end.substring(0, 4)
        val eMonth = end.substring(5, 7)
        val eDay = end.substring(8, 10)

        return if (sYear != eYear) {
            "$sDay-$sMonth-$sYear - $eDay-$eMonth-$eYear"
        } else {
            "$sDay-$sMonth - $eDay-$eMonth ($sYear)"
        }
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.select_date_range))
            .setTheme(com.google.android.material.R.style.ThemeOverlay_Material3_MaterialCalendar)
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            
            startDate = sdf.format(Date(selection.first))
            endDate = sdf.format(Date(selection.second))
            activePreset = getString(R.string.custom)
            
            renderFilters()
            loadLocations()
        }

        dateRangePicker.show(childFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun loadLocations() {
        val query = binding.editTextSearch.text?.toString() ?: ""
        val locations = repository.getAllLocations(
            searchQuery = query.ifEmpty { null },
            categoryIds = if (selectedCategoryIds.isEmpty()) null else selectedCategoryIds.toList(),
            startDate = if (startDate != null) "$startDate 00:00:00" else null,
            endDate = if (endDate != null) "$endDate 23:59:59" else null,
            withCategory = true
        )
        adapter.setSearchQuery(query)
        adapter.updateList(locations)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

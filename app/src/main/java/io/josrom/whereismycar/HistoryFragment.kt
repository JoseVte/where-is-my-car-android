package io.josrom.whereismycar

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import io.josrom.whereismycar.databinding.FragmentHistoryBinding
import org.osmdroid.config.Configuration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Configuration.getInstance().userAgentValue = requireContext().packageName
        repository = SavedLocationRepository(requireContext())
        categoryRepository = CategoryRepository(requireContext())

        setupRecyclerView()
        setupListeners()
        renderFilters()
        loadLocations()
    }

    private fun setupRecyclerView() {
        adapter = LocationAdapter(
            locations = mutableListOf(),
            onItemClick = { location ->
                val bundle = Bundle().apply {
                    putInt("locationId", location.id)
                }
                findNavController().navigate(R.id.action_LocationHistoryFragment_to_LocationDetailsFragment, bundle)
            },
            onEditClick = { location ->
                Toast.makeText(context, "Edit ${location.displayName}", Toast.LENGTH_SHORT).show()
            },
            onShareClick = { location ->
                location.share(requireContext())
            },
            onDeleteClick = { location ->
                repository.deleteLocation(location.id)
                loadLocations()
                Toast.makeText(context, "Deleted ${location.displayName}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerViewLocations.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewLocations.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

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
            addPresetChip(getString(R.string.all), false, R.drawable.ic_time) {
                startDate = null
                endDate = null
                activePreset = null
                renderFilters()
                loadLocations()
            }
        }

        // "Today" preset
        addPresetChip(getString(R.string.today), activePreset == getString(R.string.today), R.drawable.ic_time) {
            val today = getFormattedDate(0)
            startDate = today
            endDate = today
            activePreset = getString(R.string.today)
            renderFilters()
            loadLocations()
        }

        // "Yesterday" preset
        addPresetChip(getString(R.string.yesterday), activePreset == getString(R.string.yesterday), R.drawable.ic_time) {
            val yesterday = getFormattedDate(-1)
            startDate = yesterday
            endDate = yesterday
            activePreset = getString(R.string.yesterday)
            renderFilters()
            loadLocations()
        }

        // "Last 7 Days" preset
        addPresetChip(getString(R.string.last_7_days), activePreset == getString(R.string.last_7_days), R.drawable.ic_time) {
            startDate = getFormattedDate(-7)
            endDate = getFormattedDate(0)
            activePreset = getString(R.string.last_7_days)
            renderFilters()
            loadLocations()
        }

        // "Custom Range" button
        val isCustom = activePreset == getString(R.string.custom)
        val customLabel = if (isCustom && startDate != null) {
            "${startDate?.substring(5)} - ${endDate?.substring(5)}"
        } else {
            getString(R.string.custom)+"..."
        }

        addPresetChip(customLabel, isCustom, R.drawable.ic_time) {
            showDateRangePicker()
        }
    }

    private fun createFilterChip(
        label: String,
        iconRes: Int?,
        isSelected: Boolean,
        baseColor: Int = Color.WHITE
    ): MaterialButton {
        val button = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        val params = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 8.toPx(), 0)
        button.layoutParams = params
        button.text = label
        iconRes?.let { 
            button.setIconResource(it)
            button.iconSize = 18.toPx()
        }
        button.cornerRadius = 24.toPx()
        button.setPadding(12.toPx(), 4.toPx(), 12.toPx(), 4.toPx())
        button.insetTop = 0
        button.insetBottom = 0

        val alphaColor = ColorUtils.setAlphaComponent(baseColor, (255 * if (isSelected) 0.3 else 0.1).toInt())
        
        button.setBackgroundColor(alphaColor)
        button.setTextColor(baseColor)
        button.iconTint = ColorStateList.valueOf(baseColor)
        button.strokeColor = ColorStateList.valueOf(baseColor)
        button.strokeWidth = if (isSelected) 2.toPx() else 0
        
        return button
    }

    private fun addPresetChip(label: String, isSelected: Boolean, iconRes: Int? = null, onClick: () -> Unit) {
        val primaryBlue = ContextCompat.getColor(requireContext(), R.color.primary_blue)
        val color = if (isSelected) primaryBlue else Color.WHITE
        val button = createFilterChip(label, iconRes, isSelected, baseColor = color)
        button.setOnClickListener { onClick() }
        binding.dateFiltersContainer.addView(button)
    }

    private fun renderCategories() {
        binding.categoriesContainer.removeAllViews()

        // "All" button - only show if a filter is active
        if (selectedCategoryIds.isNotEmpty()) {
            val allButton = createFilterChip(
                label = getString(R.string.all),
                iconRes = R.drawable.ic_map,
                isSelected = false,
                baseColor = Color.WHITE
            )
            allButton.setOnClickListener {
                selectedCategoryIds.clear()
                renderFilters()
                loadLocations()
            }
            binding.categoriesContainer.addView(allButton)
        }

        val categories = categoryRepository.getAllCategories()
        for (category in categories) {
            val resId = resources.getIdentifier(category.icon, "drawable", requireContext().packageName)
            val isSelected = selectedCategoryIds.contains(category.id)
            val button = createCategoryButton(category, if (resId != 0) resId else R.drawable.ic_location, isSelected)
            button.setOnClickListener {
                if (selectedCategoryIds.contains(category.id)) {
                    selectedCategoryIds.remove(category.id)
                } else {
                    selectedCategoryIds.add(category.id)
                }
                renderFilters()
                loadLocations()
            }
            binding.categoriesContainer.addView(button)
        }
    }

    private fun createCategoryButton(category: Category, iconRes: Int, isSelected: Boolean): MaterialButton {
        val categoryColor = try { category.color.toColorInt() } catch (e: Exception) { Color.WHITE }
        
        return createFilterChip(
            label = category.label,
            iconRes = iconRes,
            isSelected = isSelected,
            baseColor = categoryColor
        )
    }

    private fun renderActiveFilters() {
        binding.activeFiltersGroup.removeAllViews()

        // Date filter
        activePreset?.let { preset ->
            val label = if (preset == "Custom") "${startDate?.substring(5)} - ${endDate?.substring(5)}" else preset
            val primaryBlue = ContextCompat.getColor(requireContext(), R.color.primary_blue)
            addActiveFilterChip(label, primaryBlue, R.drawable.ic_time) {
                activePreset = null
                startDate = null
                endDate = null
                renderFilters()
                loadLocations()
            }
        }

        // Category filters
        val categories = categoryRepository.getAllCategories()
        selectedCategoryIds.forEach { id ->
            val category = categories.find { it.id == id }
            category?.let {
                val categoryColor = try { it.color.toColorInt() } catch (e: Exception) { Color.WHITE }
                val iconRes = resources.getIdentifier(it.icon, "drawable", requireContext().packageName)
                addActiveFilterChip(it.label, categoryColor, if (iconRes != 0) iconRes else R.drawable.ic_location) {
                    selectedCategoryIds.remove(id)
                    renderFilters()
                    loadLocations()
                }
            }
        }

        updateActiveFiltersVisibility()
    }

    private fun addActiveFilterChip(label: String, color: Int, iconRes: Int, onRemove: () -> Unit) {
        val chip = Chip(requireContext()).apply {
            text = label
            setChipIconResource(iconRes)
            chipIconTint = ColorStateList.valueOf(color)
            isCloseIconVisible = true
            setOnCloseIconClickListener { onRemove() }
            
            val alphaBackground = ColorUtils.setAlphaComponent(color, (255 * 0.2).toInt())
            chipBackgroundColor = ColorStateList.valueOf(alphaBackground)
            setTextColor(color)
            closeIconTint = ColorStateList.valueOf(color)
            chipStrokeColor = ColorStateList.valueOf(color)
            chipStrokeWidth = 1.toPx().toFloat()

            setEnsureMinTouchTargetSize(false)
        }
        binding.activeFiltersGroup.addView(chip)
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

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun loadLocations() {
        val query = binding.editTextSearch.text?.toString() ?: ""
        val locations = repository.getAllLocations(
            searchQuery = if (query.isEmpty()) null else query,
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

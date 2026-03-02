package io.josrom.whereismycar

import android.graphics.Color
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import io.josrom.whereismycar.databinding.FragmentHistoryBinding
import org.osmdroid.config.Configuration

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: LocationAdapter
    private lateinit var repository: SavedLocationRepository
    private lateinit var categoryRepository: CategoryRepository

    private val selectedCategoryIds = mutableSetOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize OSMDroid configuration
        Configuration.getInstance().userAgentValue = requireContext().packageName
        repository = SavedLocationRepository(requireContext())
        categoryRepository = CategoryRepository(requireContext())

        setupRecyclerView()
        setupListeners()
        renderCategories()
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
            binding.categoriesScroll.isVisible = !binding.categoriesScroll.isVisible
        }
    }

    private fun renderCategories() {
        binding.categoriesContainer.removeAllViews()

        // "All" button - only if something is selected
        if (selectedCategoryIds.isNotEmpty()) {
            val allButton = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 8.toPx(), 0)
            allButton.layoutParams = params
            allButton.text = getString(R.string.all)
            allButton.setIconResource(R.drawable.ic_map)
            allButton.iconSize = 20.toPx()
            allButton.cornerRadius = 24.toPx()
            allButton.setPadding(16.toPx(), 8.toPx(), 16.toPx(), 8.toPx())
            
            allButton.setTextColor(Color.WHITE)
            allButton.iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE)
            allButton.setBackgroundColor(ColorUtils.setAlphaComponent(Color.WHITE, 77)) // ~0.3 alpha
            
            allButton.setOnClickListener {
                selectedCategoryIds.clear()
                renderCategories()
                loadLocations()
            }
            binding.categoriesContainer.addView(allButton)
        }

        // Load categories from database
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
                renderCategories()
                loadLocations()
            }
            binding.categoriesContainer.addView(button)
        }
    }

    private fun createCategoryButton(category: Category, iconRes: Int, isSelected: Boolean): MaterialButton {
        val button = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        val params = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 8.toPx(), 0)
        button.layoutParams = params
        button.text = category.label
        button.setIconResource(iconRes)
        button.iconSize = 20.toPx()
        button.setPadding(16.toPx(), 8.toPx(), 16.toPx(), 8.toPx())
        button.cornerRadius = 24.toPx()
        
        val baseColor = try { category.color.toColorInt() } catch (e: Exception) { Color.WHITE }
        val alphaColor = ColorUtils.setAlphaComponent(baseColor, (255 * 0.3).toInt())
        
        button.setBackgroundColor(alphaColor)
        button.setTextColor(baseColor)
        button.iconTint = android.content.res.ColorStateList.valueOf(baseColor)
        
        if (isSelected) {
            button.strokeWidth = 2.toPx()
            button.strokeColor = android.content.res.ColorStateList.valueOf(baseColor)
        } else {
            button.strokeWidth = 0
        }
        
        button.rippleColor = android.content.res.ColorStateList.valueOf(baseColor).withAlpha(50)
        
        return button
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun loadLocations() {
        val locations = repository.getAllLocations(
            categoryIds = if (selectedCategoryIds.isEmpty()) null else selectedCategoryIds.toList(),
            withCategory = true
        )
        adapter.updateList(locations)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package io.josrom.whereismycar

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.josrom.whereismycar.databinding.FragmentCloudSyncSettingsBinding
import io.josrom.whereismycar.databinding.LayoutHeaderBinding
import org.json.JSONArray
import org.osmdroid.config.Configuration
import java.util.Locale

class CloudSyncSettingsFragment : Fragment() {

    private var _binding: FragmentCloudSyncSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var headerBinding: LayoutHeaderBinding
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var syncManager: SyncManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        _binding = FragmentCloudSyncSettingsBinding.inflate(inflater, container, false)
        headerBinding = LayoutHeaderBinding.bind(binding.layoutHeader.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoryRepository = CategoryRepository(requireContext())
        syncManager = (activity as MainActivity).syncManager

        setupHeader()
        loadPreferences()
        setupListeners()
        setupCategoryList()
    }

    private fun setupHeader() {
        headerBinding.tvToolbarTitle.text = getString(R.string.cloud_sync)
        headerBinding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        binding.swAutoSync.isChecked = prefs.getBoolean("sync_auto", true)
        binding.swScheduledSync.isChecked = prefs.getBoolean("sync_scheduled", false)
        binding.swSyncAll.isChecked = prefs.getBoolean("sync_all_categories", true)

        updateSwitchesState()
    }

    private fun updateSwitchesState() {
        val autoSync = binding.swAutoSync.isChecked
        val scheduledSync = binding.swScheduledSync.isChecked

        binding.swScheduledSync.isEnabled = !autoSync
        binding.btnConfigureSchedule.isEnabled = !autoSync && scheduledSync
        binding.btnConfigureSchedule.alpha = if (binding.btnConfigureSchedule.isEnabled) 1.0f else 0.5f
        
        // Show/hide schedule info based on scheduled sync state
        if (scheduledSync) {
            binding.tvScheduleInfo.visibility = View.VISIBLE
            binding.tvScheduleInfo.text = getScheduleInfoText()
        } else {
            binding.tvScheduleInfo.visibility = View.GONE
        }
        
        // Make RecyclerView more opaque when sync all is checked, but keep it visible
        binding.rvCategories.alpha = if (binding.swSyncAll.isChecked) 0.5f else 1.0f
        
        // Update adapter to reflect sync all state
        (binding.rvCategories.adapter as? CategorySyncAdapter)?.updateSyncAllState(binding.swSyncAll.isChecked)
    }

    private fun setupListeners() {
        binding.swAutoSync.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                putBoolean("sync_auto", isChecked)
            }
            if (isChecked) {
                binding.swScheduledSync.isChecked = false
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putBoolean("sync_scheduled", false)
                }
            }
            updateSwitchesState()
        }

        binding.swScheduledSync.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                putBoolean("sync_scheduled", isChecked)
            }
            updateSwitchesState()
        }

        binding.btnConfigureSchedule.setOnClickListener {
            showScheduleConfigDialog()
        }

        binding.swSyncAll.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                putBoolean("sync_all_categories", isChecked)
            }
            updateSwitchesState()
        }

        binding.btnForceUpload.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.force_upload)
                .setMessage(R.string.force_upload_confirmation)
                .setPositiveButton(R.string.save) { _, _ ->
                    syncManager.forceUpload {
                        Toast.makeText(requireContext(), getString(R.string.cloud_data_updated), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnForceDownload.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.force_download)
                .setMessage(R.string.force_download_confirmation)
                .setPositiveButton(R.string.delete) { _, _ ->
                    syncManager.forceDownload {
                        Toast.makeText(requireContext(), getString(R.string.local_data_restored_from_cloud), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun setupCategoryList() {
        val categories = categoryRepository.getAllCategories()
        
        val uncategorized = Category(
            id = -1,
            label = getString(R.string.uncategorized),
            icon = "ic_forbidden",
            color = "#7D6E5D"
        )
        categories.add(0, uncategorized)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val selectedJson = prefs.getString("sync_selected_categories", "[]") ?: "[]"
        val selectedIds = try {
            val arr = JSONArray(selectedJson)
            MutableList(arr.length()) { i -> arr.getInt(i) }
        } catch (_: Exception) {
            mutableListOf()
        }

        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = CategorySyncAdapter(categories, selectedIds, binding.swSyncAll.isChecked)
    }

    private fun showScheduleConfigDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sync_schedule, null)
        
        val toggleMode = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_sync_mode)
        val llHourly = dialogView.findViewById<View>(R.id.ll_hourly_options)
        val llTime = dialogView.findViewById<View>(R.id.ll_time_options)
        val llWeekly = dialogView.findViewById<View>(R.id.ll_weekly_options)
        
        val btnSelectMinute = dialogView.findViewById<MaterialButton>(R.id.btn_select_minute)
        val btnSelectTime = dialogView.findViewById<MaterialButton>(R.id.btn_select_time)
        val cgDays = dialogView.findViewById<ChipGroup>(R.id.cg_days)

        // Load existing values
        val currentMode = prefs.getString("sync_schedule_mode", "hourly") ?: "hourly"
        when (currentMode) {
            "hourly" -> toggleMode.check(R.id.btn_hourly)
            "daily" -> toggleMode.check(R.id.btn_daily)
            "weekly" -> toggleMode.check(R.id.btn_weekly)
        }

        val savedMinute = prefs.getInt("sync_schedule_minute", 0)
        var currentMinute = savedMinute
        btnSelectMinute.text = String.format(Locale.getDefault(), "%02d", currentMinute)
        var savedHour = prefs.getInt("sync_schedule_hour", 0)
        var savedTimeMinute = prefs.getInt("sync_schedule_time_minute", 0)
        btnSelectTime.text = String.format(Locale.getDefault(), "%02d:%02d", savedHour, savedTimeMinute)

        val selectedDaysJson = prefs.getString("sync_schedule_days", "[]") ?: "[]"
        val selectedDaysArr = try { JSONArray(selectedDaysJson) } catch (_: Exception) { JSONArray() }
        for (i in 0 until selectedDaysArr.length()) {
            val dayId = selectedDaysArr.getString(i)
            val resId = resources.getIdentifier("chip_$dayId", "id", requireContext().packageName)
            if (resId != 0) {
                cgDays.findViewById<Chip>(resId)?.isChecked = true
            }
        }

        fun updateVisibility(id: Int) {
            llHourly.isVisible = id == R.id.btn_hourly
            llTime.isVisible = id == R.id.btn_daily || id == R.id.btn_weekly
            llWeekly.isVisible = id == R.id.btn_weekly
        }

        updateVisibility(toggleMode.checkedButtonId)
        toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) updateVisibility(checkedId)
        }

        btnSelectMinute.setOnClickListener {
            val numberPicker = NumberPicker(requireContext()).apply {
                minValue = 0
                maxValue = 59
                value = currentMinute
                setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.minute)
                .setView(numberPicker)
                .setPositiveButton(R.string.save) { _, _ ->
                    currentMinute = numberPicker.value
                    btnSelectMinute.text = String.format(Locale.getDefault(), "%02d", currentMinute)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        btnSelectTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(savedHour)
                .setMinute(savedTimeMinute)
                .setTitleText(R.string.time)
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .build()
            
            picker.addOnPositiveButtonClickListener {
                savedHour = picker.hour
                savedTimeMinute = picker.minute
                btnSelectTime.text = String.format(Locale.getDefault(), "%02d:%02d", savedHour, savedTimeMinute)
            }
            picker.show(childFragmentManager, "time_picker")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.configure_schedule)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val mode = when (toggleMode.checkedButtonId) {
                    R.id.btn_daily -> "daily"
                    R.id.btn_weekly -> "weekly"
                    else -> "hourly"
                }
                
                val minute = currentMinute

                val daysArr = JSONArray()
                val dayIds = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
                dayIds.forEach { day ->
                    val resId = resources.getIdentifier("chip_$day", "id", requireContext().packageName)
                    if (resId != 0 && cgDays.findViewById<Chip>(resId)?.isChecked == true) {
                        daysArr.put(day)
                    }
                }

                prefs.edit().apply {
                    putString("sync_schedule_mode", mode)
                    putInt("sync_schedule_minute", minute)
                    putInt("sync_schedule_hour", savedHour)
                    putInt("sync_schedule_time_minute", savedTimeMinute)
                    putString("sync_schedule_days", daysArr.toString())
                    apply()
                }

                binding.tvScheduleInfo.text = getScheduleInfoText()

                Toast.makeText(requireContext(), "Schedule saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getScheduleInfoText(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val mode = prefs.getString("sync_schedule_mode", "hourly") ?: "hourly"
        val minute = prefs.getInt("sync_schedule_minute", 0)
        val hour = prefs.getInt("sync_schedule_hour", 0)
        val timeMinute = prefs.getInt("sync_schedule_time_minute", 0)

        return when (mode) {
            "hourly" -> getString(R.string.schedule_info_hourly, minute)
            "daily" -> getString(R.string.schedule_info_daily, String.format(Locale.getDefault(), "%02d:%02d", hour, timeMinute))
            "weekly" -> {
                val daysJson = prefs.getString("sync_schedule_days", "[]") ?: "[]"
                val daysArr = try { JSONArray(daysJson) } catch (_: Exception) { JSONArray() }
                val days = mutableListOf<String>()
                for (i in 0 until daysArr.length()) {
                    val dayId = daysArr.getString(i)
                    val dayName = getDayNameFromId(dayId)
                    if (dayName.isNotEmpty()) {
                        days.add(dayName)
                    }
                }
                getString(R.string.schedule_info_weekly, days.joinToString(", "), String.format(Locale.getDefault(), "%02d:%02d", hour, timeMinute))
            }
            else -> ""
        }
    }

    private fun getDayNameFromId(dayId: String): String {
        return when (dayId.lowercase()) {
            "mon" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                android.text.format.DateFormat.format("EEEE", calendar.time).toString()
            }
            "tue" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.TUESDAY)
                android.text.format.DateFormat.format("EEEE", calendar.time).toString()
            }
            "wed" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.WEDNESDAY)
                android.text.format.DateFormat.format("EEEE", calendar.time).toString()
            }
            "thu" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.THURSDAY)
                android.text.format.DateFormat.format("EEEE", calendar.time).toString()
            }
            "fri" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.FRIDAY)
                android.text.format.DateFormat.format("EEEE", calendar.time).toString()
            }
            "sat" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SATURDAY)
                android.text.format.DateFormat.format("EEEE", calendar.time).toString()
            }
            "sun" -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
                android.text.format.DateFormat.format("EEEE", calendar.time).toString()
            }
            else -> ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class CategorySyncAdapter(
        private val categories: List<Category>,
        private val selectedIds: MutableList<Int>,
        private var syncAllEnabled: Boolean
    ) : RecyclerView.Adapter<CategorySyncAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.checkbox)
            val iconDrawable: ImageView = view.findViewById(R.id.iv_category_icon)
            val iconEmoji: TextView = view.findViewById(R.id.tv_category_emoji)
            val name: TextView = view.findViewById(R.id.tv_category_name)
            val categoryLayout: LinearLayout = view.findViewById(R.id.layout_category)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_sync, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            val categoryColor = category.getResolvedColor(requireContext())

            holder.name.text = category.getResolvedLabel(requireContext())
            holder.name.setTextColor(categoryColor)
            category.setupIcon(holder.iconDrawable, holder.iconEmoji, 0.0f, category.label == getString(R.string.uncategorized))

            // Create rounded background with category color and 20dp radius
            val shape = MaterialShapeDrawable(
                ShapeAppearanceModel().toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, 50f)
                    .build()
            )
            shape.setTint(categoryColor)
            shape.alpha = 100
            holder.categoryLayout.background = shape

            // Set checkbox color to category color
            holder.checkBox.buttonTintList = ColorStateList.valueOf(categoryColor)

            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = selectedIds.contains(category.id)
            
            if (syncAllEnabled) {
                // Disable checkbox and click listener when sync all is enabled
                holder.checkBox.isEnabled = false
                holder.checkBox.setOnCheckedChangeListener(null)
                holder.itemView.setOnClickListener(null)
            } else {
                // Enable checkbox and click listener when sync all is disabled
                holder.checkBox.isEnabled = true
                holder.checkBox.setOnCheckedChangeListener { _, isChecked: Boolean ->
                    if (isChecked) {
                        if (!selectedIds.contains(category.id)) selectedIds.add(category.id)
                    } else {
                        selectedIds.remove(category.id)
                    }
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                        putString("sync_selected_categories", JSONArray(selectedIds).toString())
                    }
                }
                holder.itemView.setOnClickListener { holder.checkBox.toggle() }
            }
        }

        override fun getItemCount() = categories.size

        fun updateSyncAllState(syncAll: Boolean) {
            syncAllEnabled = syncAll
            notifyDataSetChanged()
        }
    }
}

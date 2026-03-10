package io.josrom.whereismycar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.josrom.whereismycar.databinding.FragmentEditCategoryBinding
import io.josrom.whereismycar.databinding.ItemColorPickerBinding
import io.josrom.whereismycar.databinding.ItemIconPickerBinding
import androidx.core.graphics.toColorInt
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.widget.EmojiEditText
import androidx.emoji2.text.EmojiCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class EditCategoryFragment : Fragment() {

    private var _binding: FragmentEditCategoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: CategoryRepository

    private var categoryId: Int? = null
    private var selectedColor: String = "#0A84FF"
    private var selectedIcon: String = "favorite"
    private var customColor: String? = null
    private val randomEmoji = listOf("🤩", "🚀", "🎨", "🎭", "🎮", "🎸", "⛺", "🌈", "🍦", "🍩").random()

    private val presetColors = listOf(
        "#0A84FF" to "Ocean Blue",
        "#30D158" to "Green",
        "#FF453A" to "Red",
        "#FF9F0A" to "Orange",
        "#AF52DE" to "Purple",
        "#FF375F" to "Pink",
        "#5E5CE6" to "Indigo"
    )

    private val icons = listOf(
        // Row 1
        "ic_forbidden",
        "ic_custom_emoji",
        "home",
        "favorite",
        "route",
        // Row 2
        "ic_coffee",
        "ic_layers",
        "ic_history",
        "ic_settings",
        "ic_time",
        // Row 3
        "ic_walk",
        "ic_drive",
        "ic_location",
        "ic_search",
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditCategoryBinding.inflate(inflater, container, false)

        EmojiCompat.init(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = CategoryRepository(requireContext())

        categoryId = arguments?.getInt(ARG_CATEGORY_ID)?.takeIf { it != -1 }

        setupUI()
        loadCategoryData()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.categoryName.addTextChangedListener {
            binding.categoryPreviewName.text =
                it.toString().ifEmpty { getString(R.string.category) }
        }

        setupColorPicker()
        setupIconPicker()

        val saveAction = {
            val name = binding.categoryName.text.toString()
            if (name.isNotBlank()) {
                val iconToSave = if (icons.contains(selectedIcon)) {
                    "@drawable/$selectedIcon"
                } else {
                    selectedIcon
                }
                val category = Category(
                    id = categoryId ?: 0,
                    label = name,
                    icon = iconToSave,
                    color = selectedColor
                )
                if (categoryId == null) {
                    repository.insertCategory(category)
                } else {
                    repository.updateCategory(category)
                }
                parentFragmentManager.popBackStack()
            }
        }

        binding.btnSaveText.setOnClickListener { saveAction() }
        binding.btnSaveCategory.setOnClickListener { saveAction() }

        binding.btnCustomColor.setOnClickListener {
            showCustomColorPicker()
        }
    }

    private fun loadCategoryData() {
        categoryId?.let { id ->
            repository.getCategory(id)?.let { category ->
                binding.categoryName.setText(category.label)
                selectedColor = category.color
                selectedIcon = if (category.icon.startsWith("@drawable/")) {
                    category.icon.substringAfterLast("/")
                } else {
                    category.icon
                }

                if (presetColors.none { it.first.equals(selectedColor, ignoreCase = true) }) {
                    customColor = selectedColor
                }
            }
        }
        updatePreview()
    }

    private fun setupColorPicker() {
        binding.rvColors.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvColors.adapter = ColorAdapter(presetColors) { colorHex ->
            selectedColor = colorHex
            updatePreview()
            binding.rvColors.adapter?.notifyDataSetChanged()
        }
    }

    private fun showCustomColorPicker() {
        val initialColor = try {
            selectedColor.toColorInt()
        } catch (e: Exception) {
            Color.BLUE
        }

        val builder = ColorPickerDialog.Builder(requireContext())
            .setTitle(getString(R.string.color))
            .setPreferenceName("CategoryColorPicker")
            .setPositiveButton(getString(R.string.save), object : ColorEnvelopeListener {
                override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                    if (fromUser) {
                        val colorHex = "#" + envelope.hexCode
                        selectedColor = colorHex
                        customColor = colorHex
                        updatePreview()
                        binding.rvColors.adapter?.notifyDataSetChanged()
                    }
                }
            })
            .setNegativeButton(getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .attachAlphaSlideBar(false)
            .attachBrightnessSlideBar(true)

        builder.colorPickerView.setInitialColor(initialColor)
        builder.show()
    }

    private fun setupIconPicker() {
        binding.rvIcons.layoutManager = GridLayoutManager(requireContext(), 5)
        binding.rvIcons.adapter = IconAdapter(icons) { iconName ->
            if (iconName == "ic_custom_emoji") {
                showEmojiPickerDialog()
            } else {
                selectedIcon = iconName
                updatePreview()
                binding.rvIcons.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun showEmojiPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_emoji_picker, null)
        val editText = dialogView.findViewById<EmojiEditText>(R.id.et_emoji)
        
        // Initial state
        val isCurrentlySelectedEmoji = !icons.contains(selectedIcon) && selectedIcon != "ic_forbidden"
        if (isCurrentlySelectedEmoji) {
            editText.setText(selectedIcon)
            editText.setSelection(editText.text?.length ?: 0)
        }

        // Hint to the keyboard that this is for emojis/short messages
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE

        // Use InputFilter to ensure only emojis can be entered
        editText.filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
            for (i in start until end) {
                val char = source[i]

                // Block letters, digits, and whitespace
                if (Character.isLetterOrDigit(char) || Character.isWhitespace(char)) {
                    return@InputFilter ""
                }

                val type = Character.getType(char.code)
                // Common emoji character types: Surrogates, Symbols, Marks
                val isEmojiPart = type == Character.SURROGATE.toInt() ||
                        type == Character.OTHER_SYMBOL.toInt() ||
                        type == Character.NON_SPACING_MARK.toInt() ||
                        type == Character.ENCLOSING_MARK.toInt() ||
                        type == Character.OTHER_PUNCTUATION.toInt() ||
                        type == Character.MODIFIER_SYMBOL.toInt() ||
                        char.code == 0x200D // Zero Width Joiner

                if (!isEmojiPart) {
                    return@InputFilter ""
                }
            }
            null
        }, InputFilter.LengthFilter(10))

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val emoji = editText.text.toString().trim()
                if (emoji.isNotEmpty()) {
                    selectedIcon = emoji
                    updatePreview()
                    binding.rvIcons.adapter?.notifyDataSetChanged()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        
        dialog.setOnShowListener {
            editText.requestFocus()
            // Using postDelayed to ensure the view is fully attached before showing keyboard
            editText.postDelayed({
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        dialog.show()
    }

    private fun updatePreview() {
        binding.categoryPreviewName.text =
            binding.categoryName.text.toString().ifEmpty { getString(R.string.category) }
        val colorInt = try {
            selectedColor.toColorInt()
        } catch (e: Exception) {
            Color.BLUE
        }
        binding.categoryPreviewTag.backgroundTintList = ColorStateList.valueOf(colorInt)

        val isEmoji =
            selectedIcon.isNotEmpty() && !icons.contains(selectedIcon) && selectedIcon != "ic_forbidden" && selectedIcon != "ic_custom_emoji"

        if (isEmoji) {
            binding.categoryPreviewIcon.visibility = View.GONE
            binding.categoryPreviewEmoji.visibility = View.VISIBLE
            binding.categoryPreviewEmoji.text = selectedIcon
        } else if (selectedIcon == "ic_forbidden") {
            binding.categoryPreviewIcon.visibility = View.GONE
            binding.categoryPreviewEmoji.visibility = View.GONE
        } else {
            binding.categoryPreviewIcon.visibility = View.VISIBLE
            binding.categoryPreviewEmoji.visibility = View.GONE
            val iconRes =
                resources.getIdentifier(selectedIcon, "drawable", requireContext().packageName)
            if (iconRes != 0) {
                binding.categoryPreviewIcon.setImageResource(iconRes)
            }
        }

        val isCustomSelected =
            presetColors.none { it.first.equals(selectedColor, ignoreCase = true) }

        if (isCustomSelected) {
            binding.customColorView.backgroundTintList = ColorStateList.valueOf(colorInt)
            binding.customColorIcon.setImageResource(R.drawable.ic_check)
        } else {
            customColor?.let {
                binding.customColorView.backgroundTintList = ColorStateList.valueOf(it.toColorInt())
                binding.customColorIcon.setImageResource(R.drawable.ic_color_lens)
            } ?: run {
                binding.customColorView.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.primary_blue
                    )
                )
                binding.customColorIcon.setImageResource(R.drawable.ic_color_lens)
            }
        }

        val colorName =
            presetColors.find { it.first.equals(selectedColor, ignoreCase = true) }?.second
                ?: getString(R.string.custom)
        binding.colorName.text = colorName
        binding.colorName.setTextColor(colorInt)
    }

    inner class ColorAdapter(
        private val items: List<Pair<String, String>>,
        private val onSelect: (String) -> Unit
    ) :
        RecyclerView.Adapter<ColorAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemColorPickerBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemColorPickerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (colorHex, _) = items[position]
            val colorInt = colorHex.toColorInt()
            holder.binding.viewColor.backgroundTintList = ColorStateList.valueOf(colorInt)
            holder.binding.ivSelected.setImageResource(R.drawable.ic_check)
            holder.binding.ivSelected.visibility =
                if (selectedColor.equals(colorHex, ignoreCase = true)) View.VISIBLE else View.GONE
            holder.binding.ivSelected.imageTintList = ColorStateList.valueOf(Color.WHITE)

            holder.itemView.setOnClickListener { onSelect(colorHex) }
        }

        override fun getItemCount() = items.size
    }

    inner class IconAdapter(
        private val items: List<String>,
        private val onSelect: (String) -> Unit
    ) :
        RecyclerView.Adapter<IconAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemIconPickerBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemIconPickerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val iconName = items[position]
            val context = holder.itemView.context

            val isCustomEmojiPlaceholder = iconName == "ic_custom_emoji"
            val isCurrentlySelectedEmoji =
                !icons.contains(selectedIcon) && selectedIcon != "ic_forbidden"

            if (isCustomEmojiPlaceholder) {
                holder.binding.ivIcon.visibility = View.GONE
                holder.binding.tvEmoji.visibility = View.VISIBLE
                holder.binding.tvEmoji.text = if (isCurrentlySelectedEmoji) selectedIcon else randomEmoji
            } else {
                holder.binding.ivIcon.visibility = View.VISIBLE
                holder.binding.tvEmoji.visibility = View.GONE
                val iconRes =
                    context.resources.getIdentifier(iconName, "drawable", context.packageName)
                if (iconRes != 0) {
                    holder.binding.ivIcon.setImageResource(iconRes)
                }
            }

            val isSelected =
                if (isCustomEmojiPlaceholder) isCurrentlySelectedEmoji else selectedIcon == iconName

            if (isSelected) {
                val colorInt = selectedColor.toColorInt()
                holder.binding.cvIconBackground.setCardBackgroundColor(colorInt)
                holder.binding.cvIconBackground.strokeColor = colorInt
                holder.binding.ivIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                holder.binding.tvEmoji.setTextColor(Color.WHITE)
            } else {
                holder.binding.cvIconBackground.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_surface))
                holder.binding.cvIconBackground.strokeColor = ContextCompat.getColor(context, R.color.border_gray)
                holder.binding.ivIcon.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
                holder.binding.tvEmoji.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }

            holder.itemView.setOnClickListener { onSelect(iconName) }
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY_ID = "category_id"

        fun newInstance(categoryId: Int = -1): EditCategoryFragment {
            return EditCategoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CATEGORY_ID, categoryId)
                }
            }
        }
    }
}

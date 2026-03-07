package io.josrom.whereismycar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.google.android.material.chip.Chip

fun createFilterChip(
    context: Context,
    label: String,
    iconRes: Int?,
    isSelected: Boolean,
    baseColor: Int,
    onClick: () -> Unit
): Chip {
    val chip = Chip(context)
    chip.text = label
    if (iconRes != null && iconRes != 0) {
        chip.chipIcon = ResourcesCompat.getDrawable(context.resources, iconRes, context.theme)
        chip.isChipIconVisible = true
    }
    
    chip.isCheckable = false
    chip.chipStrokeWidth = 0f
    
    if (isSelected) {
        chip.chipBackgroundColor = ColorStateList.valueOf(baseColor)
        chip.setTextColor(Color.WHITE)
        chip.chipIconTint = ColorStateList.valueOf(Color.WHITE)
    } else {
        val alphaColor = ColorUtils.setAlphaComponent(baseColor, (255 * 0.15).toInt())
        chip.chipBackgroundColor = ColorStateList.valueOf(alphaColor)
        chip.setTextColor(baseColor)
        chip.chipIconTint = ColorStateList.valueOf(baseColor)
    }
    
    // Add margin for use in LinearLayout
    val params = ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    params.setMargins(0, 0, (8 * context.resources.displayMetrics.density).toInt(), 0)
    chip.layoutParams = params
    
    chip.setOnClickListener { onClick() }
    
    return chip
}

fun Category.createChip(
    context: Context,
    isSelected: Boolean,
    onClick: () -> Unit
): Chip {
    val categoryColor = try { this.color.toColorInt() } catch (_: Exception) { Color.WHITE }
    val iconRes = context.resources.getIdentifier(this.icon, "drawable", context.packageName)
    
    val labelText = if (this.label.startsWith("@string/")) {
        val resourceName = this.label.substringAfterLast("/")
        val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)
        if (resourceId != 0) context.getString(resourceId) else this.label
    } else {
        this.label
    }

    return createFilterChip(
        context = context,
        label = labelText,
        iconRes = if (iconRes != 0) iconRes else null,
        isSelected = isSelected,
        baseColor = categoryColor,
        onClick = onClick
    )
}

package io.josrom.whereismycar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.google.android.material.chip.Chip
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/** Default categories store a string resource as label: translated on display and not editable. */
val Category.isSystem: Boolean
    get() = label.startsWith(SYSTEM_LABEL_PREFIX)

const val SYSTEM_LABEL_PREFIX = "@string/"

fun Category.getResolvedLabel(context: Context): String {
    return if (this.isSystem) {
        val resourceName = this.label.substringAfterLast("/")
        val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)
        if (resourceId != 0) context.getString(resourceId) else this.label
    } else {
        this.label
    }
}

fun Category.getResolvedColor(context: Context? = null): Int {
    return try {
        this.color.toColorInt()
    } catch (_: Exception) {
        if (context != null) {
            ContextCompat.getColor(context, R.color.text_primary)
        } else {
            Color.GRAY
        }
    }
}

fun Int.withAlpha(alpha: Float): Int {
    return ColorUtils.setAlphaComponent(this, (255 * alpha).toInt())
}

fun Category.setupIcon(drawableView: ImageView, emojiView: TextView, alpha: Float = 0.15f, forbiddenShow: Boolean = false) {
    val context = drawableView.context
    val categoryColor = this.getResolvedColor(context)
    val alphaBackground = categoryColor.withAlpha(alpha)
    val isForbidden = this.icon == "ic_forbidden" || this.icon == "@drawable/ic_forbidden"

    // Apply background tint consistently
    drawableView.backgroundTintList = ColorStateList.valueOf(alphaBackground)
    emojiView.backgroundTintList = ColorStateList.valueOf(alphaBackground)

    if (isForbidden) {
        emojiView.visibility = View.GONE
        drawableView.visibility = View.VISIBLE
        drawableView.imageTintList = ColorStateList.valueOf(categoryColor)
        if (forbiddenShow) {
            drawableView.setImageResource(R.drawable.ic_forbidden)
        } else {
            drawableView.setImageDrawable(null)
        }
    } else if (this.icon.startsWith("@drawable/")) {
        emojiView.visibility = View.GONE
        drawableView.visibility = View.VISIBLE
        drawableView.imageTintList = ColorStateList.valueOf(categoryColor)
        
        val resourceName = this.icon.substringAfterLast("/")
        val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        if (resourceId != 0) {
            drawableView.setImageResource(resourceId)
        } else {
            drawableView.setImageDrawable(null)
        }
    } else if (this.icon.isNotEmpty()) {
        emojiView.visibility = View.VISIBLE
        drawableView.visibility = View.GONE
        emojiView.text = this.icon
        emojiView.setTextColor(categoryColor)
    } else {
        emojiView.visibility = View.GONE
        drawableView.visibility = View.VISIBLE
        drawableView.imageTintList = ColorStateList.valueOf(categoryColor)
        drawableView.setImageDrawable(null)
    }
}

fun Category.setupBadge(textView: TextView, alpha: Float = 0.3f) {
    val context = textView.context
    val color = this.getResolvedColor(context)
    textView.text = this.getResolvedLabel(context)
    textView.setTextColor(color)
    
    val alphaColor = color.withAlpha(alpha)
    val shape = MaterialShapeDrawable(
        ShapeAppearanceModel().toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, 16f)
            .build()
    )
    shape.setTint(alphaColor)
    textView.background = shape
}

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
    } else {
        chip.isChipIconVisible = false
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
    forbiddenShow: Boolean = false,
    onClick: () -> Unit
): Chip {
    val categoryColor = this.getResolvedColor(context)
    val isForbidden = this.icon == "ic_forbidden" || this.icon == "@drawable/ic_forbidden"
    val iconRes = if (isForbidden && !forbiddenShow) {
        0
    } else {
        val resourceName = if (this.icon.startsWith("@drawable/")) this.icon.substringAfterLast("/") else this.icon
        context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    }
    
    return createFilterChip(
        context = context,
        label = this.getResolvedLabel(context),
        iconRes = if (iconRes != 0) iconRes else null,
        isSelected = isSelected,
        baseColor = categoryColor,
        onClick = onClick
    )
}

fun Category.confirmAndDelete(
    context: Context,
    repository: CategoryRepository,
    onDeleted: () -> Unit
) {
    showDeleteConfirmationDialog(
        context = context,
        titleRes = R.string.delete_category_confirmation_title,
        messageRes = R.string.delete_category_confirmation_message,
        onConfirm = {
            repository.deleteCategory(this.id)
            onDeleted()
        }
    )
}

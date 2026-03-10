package io.josrom.whereismycar

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showDeleteConfirmationDialog(
    context: Context,
    @StringRes titleRes: Int,
    @StringRes messageRes: Int? = null,
    onConfirm: () -> Unit
) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val confirmDeletion = prefs.getBoolean("confirm_deletion", true)

    if (!confirmDeletion) {
        onConfirm()
    } else {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_delete, null)
        val checkBox = dialogView.findViewById<CheckBox>(R.id.cbDontAskAgain)
        
        messageRes?.let {
            val messageView = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
            messageView.text = context.getString(it)
        }

        val primaryBlue = ContextCompat.getColor(context, R.color.primary_blue)
        checkBox.buttonTintList = ColorStateList.valueOf(primaryBlue)

        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.iconColor, typedValue, true)
        val iconColor = if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }

        val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete)?.apply {
            setTint(iconColor)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setIcon(deleteIcon)
            .setTitle(titleRes)
            .setView(dialogView)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (checkBox.isChecked) {
                    prefs.edit { putBoolean("confirm_deletion", false) }
                }
                onConfirm()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(primaryBlue)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(primaryBlue)
    }
}

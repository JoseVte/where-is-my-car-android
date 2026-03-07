package io.josrom.whereismycar

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

fun SavedLocation.share(context: Context) {
    val uri = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
    val shareText = if (displayName.isNotEmpty()) {
        "$displayName\n$uri"
    } else {
        uri
    }

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}

fun SavedLocation.confirmAndDelete(
    context: Context,
    repository: SavedLocationRepository,
    onDeleted: () -> Unit
) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val dontAskAgain = prefs.getBoolean("dont_ask_delete_confirmation", false)

    if (dontAskAgain) {
        repository.deleteLocation(this.id)
        onDeleted()
    } else {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_delete, null)
        val checkBox = dialogView.findViewById<CheckBox>(R.id.cbDontAskAgain)

        MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.delete_confirmation_title)
            .setView(dialogView)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (checkBox.isChecked) {
                    prefs.edit { putBoolean("dont_ask_delete_confirmation", true) }
                }
                repository.deleteLocation(this.id)
                onDeleted()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

fun SavedLocation.formattedCoordinates(context: Context): String {
    val lat = String.format(Locale.getDefault(), "%.4f", abs(this.latitude))
    val lon = String.format(Locale.getDefault(), "%.4f", abs(this.longitude))
    val latDir = if (this.latitude >= 0) context.getString(R.string.north_char) else context.getString(R.string.south_char)
    val lonDir = if (this.longitude >= 0) context.getString(R.string.east_char) else context.getString(R.string.wester_char)
    return context.getString(R.string.coords_format, lat, latDir, lon, lonDir)
}

val SavedLocation.formattedDate: String
    get() {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEEE, d MMMM yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(this.date)
            if (date != null) {
                outputFormat.format(date).split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            } else {
                this.date
            }
        } catch (e: Exception) {
            this.date
        }
    }

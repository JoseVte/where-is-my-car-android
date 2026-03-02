package io.josrom.whereismycar

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Locale

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

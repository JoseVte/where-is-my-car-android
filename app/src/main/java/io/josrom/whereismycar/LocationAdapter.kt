package io.josrom.whereismycar

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.josrom.whereismycar.databinding.ItemLocationBinding
import java.util.Locale

class LocationAdapter(
    private val locations: MutableList<SavedLocation>,
    private val onItemClick: (SavedLocation) -> Unit,
    private val onEditClick: (SavedLocation) -> Unit
) : RecyclerView.Adapter<LocationAdapter.ViewHolder>() {

    private var searchQuery: String = ""

    class ViewHolder(val binding: ItemLocationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val location = locations[position]
        
        val title = location.displayName.ifEmpty { "Saved Location" }
        holder.binding.textViewTitle.text = getHighlightedText(title, searchQuery)
        
        // Example: "24 mins ago • 0.3 miles" - You might want to calculate real distance/time here
        val dateInfo = location.date
        holder.binding.textViewSubtitle.text = getHighlightedText(dateInfo, searchQuery)
        
        // Icon logic (you can expand this based on location type if you have it)
        holder.binding.imageViewIcon.setImageResource(R.drawable.ic_coffee)

        holder.itemView.setOnClickListener {
            onItemClick(location)
        }

        holder.binding.buttonEdit.setOnClickListener {
            onEditClick(location)
        }
    }

    private fun getHighlightedText(text: String, query: String): CharSequence {
        if (query.isEmpty()) return text
        
        val spannable = SpannableString(text)
        val lowerText = text.lowercase(Locale.ROOT)
        val lowerQuery = query.lowercase(Locale.ROOT)
        
        var start = lowerText.indexOf(lowerQuery)
        while (start >= 0) {
            val end = start + query.length
            spannable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = lowerText.indexOf(lowerQuery, end)
        }
        return spannable
    }

    override fun getItemCount() = locations.size

    fun setSearchQuery(query: String) {
        this.searchQuery = query
        notifyDataSetChanged()
    }

    fun addLocations(newLocations: List<SavedLocation>) {
        val startPosition = locations.size
        locations.addAll(newLocations)
        notifyItemRangeInserted(startPosition, newLocations.size)
    }

    fun clear() {
        locations.clear()
        notifyDataSetChanged()
    }

    fun clearAndAdd(newLocations: List<SavedLocation>) {
        locations.clear()
        locations.addAll(newLocations)
        notifyDataSetChanged()
    }
}
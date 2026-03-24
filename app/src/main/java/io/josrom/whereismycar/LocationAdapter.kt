package io.josrom.whereismycar

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import io.josrom.whereismycar.databinding.ItemLocationBinding
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import java.util.Locale

class LocationAdapter(
    private var locations: MutableList<SavedLocation>,
    private val onItemClick: (SavedLocation) -> Unit,
    private val onDeleteClick: (SavedLocation) -> Unit,
    private val onEditClick: ((SavedLocation) -> Unit)? = null,
    private val onShareClick: ((SavedLocation) -> Unit)? = null
) : RecyclerView.Adapter<LocationAdapter.ViewHolder>() {

    private var searchQuery: String = ""

    class ViewHolder(val binding: ItemLocationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val location = locations[position]
        
        val title = location.displayName.ifEmpty { holder.itemView.context.getString(R.string.saved_location_default) }
        holder.binding.textViewTitle.text = getHighlightedText(title, searchQuery)
        holder.binding.textViewSubtitle.text = getHighlightedText(location.address, searchQuery)
        holder.binding.textViewTime.text = location.formattedDate

        if (location.category != null) {
            holder.binding.textViewCategory.visibility = View.VISIBLE
            location.category.setupBadge(holder.binding.textViewCategory)
        } else {
            holder.binding.textViewCategory.visibility = View.GONE
        }
        
        // OSMDroid Map configuration
        with(holder.binding.mapView) {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(18.0)
            val point = GeoPoint(location.latitude, location.longitude)
            controller.setCenter(point)
            
            // To prevent interaction in the list
            setOnTouchListener { _, _ -> true }
        }

        holder.itemView.setOnClickListener {
            onItemClick(location)
        }

        holder.binding.buttonMore.setOnClickListener { view ->
            // Apply the theme overlay to ensure the popup correctly inherits custom attributes
            val context = ContextThemeWrapper(view.context, R.style.Theme_WhereIsMyCar_PopupMenuOverlay)
            val popup = PopupMenu(context, view, Gravity.END)
            popup.menuInflater.inflate(R.menu.location_item_menu, popup.menu)
            
            // Force icons to show in PopupMenu to match Material 3 guidelines
            try {
                val fields = popup.javaClass.declaredFields
                for (field in fields) {
                    if ("mPopup" == field.name) {
                        field.isAccessible = true
                        val menuPopupHelper = field.get(popup)
                        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                        val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                        setForceIcons.invoke(menuPopupHelper, true)
                        break
                    }
                }
            } catch (e: Exception) {
                // Fallback if reflection fails
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onEditClick?.invoke(location)
                        true
                    }
                    R.id.action_share -> {
                        onShareClick?.invoke(location)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClick(location)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
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

    @SuppressLint("NotifyDataSetChanged")
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
        val size = locations.size
        locations.clear()
        notifyItemRangeRemoved(0, size)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newLocations: List<SavedLocation>) {
        locations = newLocations.toMutableList()
        notifyDataSetChanged()
    }
}

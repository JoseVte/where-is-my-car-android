package io.josrom.whereismycar

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.josrom.whereismycar.databinding.ItemLocationBinding

class LocationAdapter(
    private val locations: List<SavedLocation>,
    private val onItemClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<LocationAdapter.ViewHolder>() {
    private var _context: Context? = null

    class ViewHolder(val binding: ItemLocationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        _context = parent.context
        val binding = ItemLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val location = locations[position]
        holder.binding.textViewTitle.text = _context?.getString(
            R.string.locationAddress,
            location.displayName,
            location.city,
            location.country
        )
        holder.binding.textViewSubtitle.text = _context?.getString(
            R.string.locationCoordinates,
            location.latitude.toString(),
            location.longitude.toString(),
            location.date
        )
        
        holder.itemView.setOnClickListener {
            onItemClick(location.id)
        }

        holder.binding.buttonDelete.setOnClickListener {
            onDeleteClick(location.id)
        }
    }

    override fun getItemCount() = locations.size
}
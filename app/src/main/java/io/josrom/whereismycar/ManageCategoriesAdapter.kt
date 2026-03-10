package io.josrom.whereismycar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import io.josrom.whereismycar.databinding.ItemCategoryBinding

class ManageCategoriesAdapter(
    private var categories: List<Category>,
    private val repository: CategoryRepository,
    private val onEditClick: (Category) -> Unit,
    private val onDeleteClick: (Category) -> Unit
) : RecyclerView.Adapter<ManageCategoriesAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        val context = holder.itemView.context

        holder.binding.categoryName.text = category.label
        
        val count = repository.getLocationCountForCategory(category.id)
        holder.binding.locationCount.text = context.getString(R.string.location_count_text, count)

        val color = try { category.color.toColorInt() } catch (e: Exception) { Color.BLUE }
        holder.binding.categoryIconContainer.setCardBackgroundColor(color)

        val iconRes = context.resources.getIdentifier(category.icon, "drawable", context.packageName)
        if (iconRes != 0) {
            holder.binding.categoryIcon.setImageResource(iconRes)
        }

        holder.binding.edit.setOnClickListener { onEditClick(category) }
        holder.binding.delete.setOnClickListener { onDeleteClick(category) }
    }

    override fun getItemCount() = categories.size

    fun updateData(newCategories: List<Category>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}

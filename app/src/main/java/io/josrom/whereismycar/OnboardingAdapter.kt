package io.josrom.whereismycar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(
    private val pages: List<OnboardingPage>,
    private val onSkipClicked: () -> Unit
) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.onboarding_image)
        val title: TextView = view.findViewById(R.id.onboarding_text)
        val description: TextView = view.findViewById(R.id.onboarding_description)
        val skipButton: View = view.findViewById(R.id.onboarding_skip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        
        holder.image.setImageResource(page.imageRes)
        holder.title.text = page.title
        holder.description.text = page.description
        
        holder.skipButton.isVisible = page.showSkip
        holder.skipButton.setOnClickListener {
            onSkipClicked()
        }
    }

    override fun getItemCount() = pages.size

    override fun getItemViewType(position: Int) = pages[position].layoutRes
}

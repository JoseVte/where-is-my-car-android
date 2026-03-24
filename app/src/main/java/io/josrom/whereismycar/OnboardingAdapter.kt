package io.josrom.whereismycar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(
    private val pages: List<OnboardingPage>,
    private val onSkipClicked: () -> Unit
) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Find skip button if it exists in the layout
        holder.view.findViewById<View>(R.id.onboarding_skip)?.setOnClickListener {
            onSkipClicked()
        }
    }

    override fun getItemCount() = pages.size

    override fun getItemViewType(position: Int) = pages[position].layoutRes
}
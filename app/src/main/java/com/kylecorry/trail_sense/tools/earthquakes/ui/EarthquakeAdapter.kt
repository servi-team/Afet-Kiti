package com.kylecorry.trail_sense.tools.earthquakes.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kylecorry.trail_sense.databinding.ItemEarthquakeBinding
import com.kylecorry.trail_sense.tools.earthquakes.domain.Earthquake

class EarthquakeAdapter(
    private val onClick: (Earthquake) -> Unit
) : ListAdapter<Earthquake, EarthquakeAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEarthquakeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemEarthquakeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(earthquake: Earthquake) {
            EarthquakeItemBinder.bind(binding, earthquake)
            binding.root.setOnClickListener { onClick(earthquake) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Earthquake>() {
        override fun areItemsTheSame(oldItem: Earthquake, newItem: Earthquake): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Earthquake, newItem: Earthquake): Boolean {
            return oldItem == newItem
        }
    }
}

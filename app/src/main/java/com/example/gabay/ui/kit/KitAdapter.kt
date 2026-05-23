package com.example.gabay.ui.kit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.gabay.databinding.ItemKitBinding
import com.example.gabay.data.models.EmergencyKitItem

class KitAdapter(
    private val onItemChecked: (EmergencyKitItem, Boolean) -> Unit
) : ListAdapter<EmergencyKitItem, KitAdapter.KitViewHolder>(KitDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KitViewHolder {
        val binding = ItemKitBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return KitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.toLongOrNull() ?: position.toLong()
    }

    inner class KitViewHolder(
        private val binding: ItemKitBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EmergencyKitItem) {
            binding.tvItemName.text = item.name
            binding.tvItemDescription.text = item.description
            binding.tvItemQuantity.text = "Quantity: ${item.calculatedQuantity}"

            binding.checkboxItem.setOnCheckedChangeListener(null)
            binding.checkboxItem.isChecked = item.isChecked

            binding.checkboxItem.setOnCheckedChangeListener { _, isChecked ->
                onItemChecked(item, isChecked)
            }
        }
    }

    class KitDiffCallback : DiffUtil.ItemCallback<EmergencyKitItem>() {
        override fun areItemsTheSame(
            oldItem: EmergencyKitItem,
            newItem: EmergencyKitItem
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: EmergencyKitItem,
            newItem: EmergencyKitItem
        ): Boolean {
            return oldItem.name == newItem.name &&
                    oldItem.isChecked == newItem.isChecked &&
                    oldItem.calculatedQuantity == newItem.calculatedQuantity &&
                    oldItem.description == newItem.description
        }

        override fun getChangePayload(oldItem: EmergencyKitItem, newItem: EmergencyKitItem): Any? {
            return if (oldItem.isChecked != newItem.isChecked) {
                "CHECKED_CHANGED"
            } else if (oldItem.calculatedQuantity != newItem.calculatedQuantity) {
                "QUANTITY_CHANGED"
            } else {
                null
            }
        }
    }
}
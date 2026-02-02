package com.example.gabay.ui.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.gabay.databinding.ItemContactBinding
import com.example.gabay.data.models.EmergencyContact

class ContactsAdapter(
    private val onCallClick: (EmergencyContact) -> Unit
) : ListAdapter<EmergencyContact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: EmergencyContact) {
            binding.tvContactName.text = contact.name
            binding.tvContactNumber.text = contact.number
            binding.tvContactType.text = contact.type.name.replace("_", " ")

            binding.btnCall.setOnClickListener {
                onCallClick(contact)
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<EmergencyContact>() {
        override fun areItemsTheSame(
            oldItem: EmergencyContact,
            newItem: EmergencyContact
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: EmergencyContact,
            newItem: EmergencyContact
        ): Boolean {
            return oldItem == newItem
        }
    }
}
package com.example.gabay.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.gabay.R
import com.example.gabay.databinding.ItemContactBinding
import com.example.gabay.data.models.EmergencyContact
import com.example.gabay.data.models.PhoneNumber

sealed class ContactListItem {
    data class Header(val title: String) : ContactListItem()
    data class Contact(val contact: EmergencyContact) : ContactListItem()
}

class ContactsAdapter(
    private val onCallClick: (PhoneNumber) -> Unit
) : ListAdapter<ContactListItem, RecyclerView.ViewHolder>(ContactListDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTACT = 1
    }

    private val expandedIds = mutableSetOf<String>()

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ContactListItem.Header -> VIEW_TYPE_HEADER
        is ContactListItem.Contact -> VIEW_TYPE_CONTACT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_contact_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val binding = ItemContactBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ContactViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ContactListItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is ContactListItem.Contact -> (holder as ContactViewHolder).bind(item.contact)
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvHeader: TextView = view.findViewById(R.id.tv_section_header)
        fun bind(title: String) { tvHeader.text = title }
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: EmergencyContact) {
            binding.tvContactName.text = contact.name
            binding.tvContactType.text = contact.type.name.replace("_", " ")

            val isExpanded = contact.id in expandedIds
            val hasMultiple = contact.numbers.size > 1

            binding.tvContactNumber.text = contact.primaryNumber

            if (hasMultiple) {
                binding.tvExpandIndicator.visibility = View.VISIBLE
                binding.tvExpandIndicator.text = if (isExpanded)
                    "▲ Show less"
                else
                    "▼ ${contact.numbers.size} numbers — tap to see all"
            } else {
                binding.tvExpandIndicator.visibility = View.GONE
            }

            binding.layoutExtraNumbers.removeAllViews()
            if (isExpanded && hasMultiple) {
                binding.layoutExtraNumbers.visibility = View.VISIBLE
                contact.numbers.forEachIndexed { index, phone ->
                    val row = LayoutInflater.from(binding.root.context)
                        .inflate(R.layout.item_phone_row, binding.layoutExtraNumbers, false)

                    val labelText = if (index == 0) "${phone.label} (Primary)" else phone.label
                    row.findViewById<TextView>(R.id.tv_phone_label).text = labelText
                    row.findViewById<TextView>(R.id.tv_phone_number).text = phone.number

                    row.findViewById<View>(R.id.btn_call_number).setOnClickListener {
                        onCallClick(phone)
                    }
                    binding.layoutExtraNumbers.addView(row)
                }
            } else {
                binding.layoutExtraNumbers.visibility = View.GONE
            }

            binding.btnCall.setOnClickListener {
                onCallClick(contact.numbers.first())
            }

            binding.root.setOnClickListener {
                if (hasMultiple) {
                    if (contact.id in expandedIds) expandedIds.remove(contact.id)
                    else expandedIds.add(contact.id)
                    notifyItemChanged(adapterPosition)
                }
            }
        }
    }

    class ContactListDiffCallback : DiffUtil.ItemCallback<ContactListItem>() {
        override fun areItemsTheSame(old: ContactListItem, new: ContactListItem): Boolean {
            return when {
                old is ContactListItem.Header && new is ContactListItem.Header -> old.title == new.title
                old is ContactListItem.Contact && new is ContactListItem.Contact -> old.contact.id == new.contact.id
                else -> false
            }
        }
        override fun areContentsTheSame(old: ContactListItem, new: ContactListItem) = old == new
    }
}
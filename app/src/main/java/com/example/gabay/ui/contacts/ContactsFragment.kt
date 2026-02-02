package com.example.gabay.ui.contacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gabay.databinding.FragmentContactsBinding
import com.example.gabay.data.repository.ContactRepository

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: ContactRepository
    private var adapter: ContactsAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = ContactRepository(requireContext())
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val allContacts = repository.getNationwideContacts() +
                repository.getLocalContacts("Calabarzon")

        adapter = ContactsAdapter { contact ->
            makePhoneCall(contact.number)
        }

        binding.recyclerContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ContactsFragment.adapter
            setHasFixedSize(true)
        }

        adapter?.submitList(allContacts)
    }

    private fun makePhoneCall(number: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerContacts.adapter = null
        adapter = null
        _binding = null
    }
}
package com.example.gabay.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gabay.databinding.FragmentContactsBinding
import com.example.gabay.data.models.PhoneNumber
import com.example.gabay.data.repository.ContactRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: ContactRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupRecyclerView()
        loadContactsWithLocation()
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter { phone: PhoneNumber ->
            makePhoneCall(phone.number)
        }

        binding.recyclerContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ContactsFragment.adapter
            setHasFixedSize(false)
        }
    }

    private fun loadContactsWithLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val city = if (location != null) {
                    getCityFromLocation(location.latitude, location.longitude)
                } else null

                buildContactList(city)
            }.addOnFailureListener {
                buildContactList(null)
            }
        } else {
            buildContactList(null)
        }
    }

    private fun getCityFromLocation(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.subAdminArea
        } catch (e: Exception) {
            null
        }
    }

    private fun buildContactList(detectedCity: String?) {
        val nationwide = repository.getNationwideContacts()
        val local = repository.getLocalAuthorityContacts(detectedCity ?: "")

        val localSectionTitle = if (detectedCity != null) {
            "Local Authorities & Agencies — $detectedCity"
        } else {
            "Local Authorities & Agencies"
        }

        val items = mutableListOf<ContactListItem>().apply {
            add(ContactListItem.Header("Emergency Hotlines"))
            addAll(nationwide.map { ContactListItem.Contact(it) })
            add(ContactListItem.Header(localSectionTitle))
            addAll(local.map { ContactListItem.Contact(it) })
        }

        adapter?.submitList(items)
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
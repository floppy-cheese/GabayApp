package com.example.gabay.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.gabay.R
import com.example.gabay.databinding.FragmentHomeBinding
import com.example.gabay.data.repository.LocationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: HomeViewModel
    private var locationJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = LocationRepository(requireContext())
        val factory = HomeViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        setupUI()
        observeLocation()
    }

    private fun setupUI() {
        binding.btnEvacuation.setOnClickListener {
            findNavController().navigate(R.id.evacuationFragment)
        }
        binding.btnEmergencyKit.setOnClickListener {
            findNavController().navigate(R.id.kitFragment)
        }
        binding.btnContacts.setOnClickListener {
            findNavController().navigate(R.id.contactsFragment)
        }
        binding.btnLessons.setOnClickListener {
            findNavController().navigate(R.id.lessonsFragment)
        }
    }

    private fun observeLocation() {
        viewModel.locationInfo.observe(viewLifecycleOwner) { info ->
            _binding?.let { binding ->
                info?.let {
                    binding.tvLocation.text = "📍 ${it.barangay}, ${it.city}, ${it.province}"
                } ?: run {
                    binding.tvLocation.text = "📍 Locating..."
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationJob?.cancel()
        locationJob = null
        _binding = null
    }
}
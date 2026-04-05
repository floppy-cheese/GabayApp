package com.example.gabay.ui.home

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.work.*
import com.example.gabay.R
import com.example.gabay.data.models.DisasterAlert
import com.example.gabay.data.models.Severity
import com.example.gabay.data.repository.DisasterRepository
import com.example.gabay.data.repository.LocationRepository
import com.example.gabay.databinding.FragmentHomeBinding
import com.example.gabay.util.AlertNotificationManager
import com.example.gabay.worker.DisasterCheckWorker
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: HomeViewModel

    // Tracks whether the first live fetch has completed
    // Prevents notifying user on cached/stale data at startup
    private var hasCompletedFirstLiveFetch = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val locationRepo = LocationRepository(requireContext())
        val disasterRepo = DisasterRepository(requireContext())
        val factory = HomeViewModelFactory(locationRepo, disasterRepo)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        AlertNotificationManager.createNotificationChannels(requireContext())
        scheduleBackgroundDisasterCheck()
        setupUI()
        observeLocation()
        observeDisasterAlerts()
    }

    override fun onResume() {
        super.onResume()
        viewModel.startPeriodicRefresh()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPeriodicRefresh()
    }

    private fun scheduleBackgroundDisasterCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DisasterCheckWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "disaster_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
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

        viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            location?.let {
                requireContext().getSharedPreferences(
                    "location_cache", android.content.Context.MODE_PRIVATE
                )
                    .edit()
                    .putFloat("last_lat", it.latitude.toFloat())
                    .putFloat("last_lon", it.longitude.toFloat())
                    .apply()
            }
        }
    }

    private fun observeDisasterAlerts() {
        viewModel.isLoadingAlerts.observe(viewLifecycleOwner) { isLoading ->
            _binding?.let { binding ->
                if (isLoading && !hasCompletedFirstLiveFetch) {
                    // Show neutral loading state — don't show stale cache
                    binding.cardDisasterAlert.setCardBackgroundColor(
                        resources.getColor(android.R.color.white, null)
                    )
                    binding.tvAlertTitle.text = "Current Disaster Status"
                    binding.tvAlertTitle.setTextColor(
                        resources.getColor(android.R.color.holo_green_dark, null)
                    )
                    binding.tvAlertMessage.text = "🔄 Checking for alerts..."
                    binding.tvAlertMessage.setTextColor(
                        resources.getColor(android.R.color.darker_gray, null)
                    )
                }
            }
        }

        viewModel.isLiveFetch.observe(viewLifecycleOwner) { isLive ->
            if (isLive) hasCompletedFirstLiveFetch = true
        }

        viewModel.disasterAlerts.observe(viewLifecycleOwner) { alerts ->
            _binding?.let { binding ->
                val activeAlerts = alerts.filter { it.isActive }
                if (activeAlerts.isEmpty()) {
                    binding.cardDisasterAlert.setCardBackgroundColor(
                        resources.getColor(android.R.color.white, null)
                    )
                    binding.tvAlertTitle.text = "Current Disaster Status"
                    binding.tvAlertTitle.setTextColor(
                        resources.getColor(android.R.color.holo_green_dark, null)
                    )
                    binding.tvAlertMessage.text = "✅ No active disasters within 200 km"
                    binding.tvAlertMessage.setTextColor(
                        resources.getColor(android.R.color.darker_gray, null)
                    )
                } else {
                    val mostSevere = activeAlerts.maxByOrNull { it.severity.ordinal }!!
                    updateAlertCard(mostSevere)

                    // Only notify after first live fetch completes
                    // This prevents notifying on stale cached data at startup
                    if (hasCompletedFirstLiveFetch &&
                        (mostSevere.severity == Severity.HIGH ||
                                mostSevere.severity == Severity.CRITICAL)
                    ) {
                        AlertNotificationManager.sendDisasterNotification(
                            requireContext(), mostSevere
                        )
                    }
                }
            }
        }
    }

    private fun updateAlertCard(alert: DisasterAlert) {
        _binding?.let { binding ->
            val (bgColor, textColor, emoji, actionText) = when (alert.severity) {
                Severity.CRITICAL -> Quad(
                    android.R.color.holo_red_dark,
                    android.R.color.white,
                    "🚨",
                    "EVACUATE NOW"
                )
                Severity.HIGH -> Quad(
                    android.R.color.holo_orange_dark,
                    android.R.color.white,
                    "⚠️",
                    "Prepare to evacuate"
                )
                Severity.MODERATE -> Quad(
                    android.R.color.holo_orange_light,
                    android.R.color.black,
                    "⚠️",
                    "Stay alert"
                )
                Severity.LOW -> Quad(
                    android.R.color.white,
                    android.R.color.darker_gray,
                    "ℹ️",
                    "Monitor situation"
                )
            }

            binding.cardDisasterAlert.setCardBackgroundColor(
                resources.getColor(bgColor, null)
            )
            binding.tvAlertTitle.setTextColor(resources.getColor(textColor, null))
            binding.tvAlertTitle.text = "$emoji $actionText"
            binding.tvAlertMessage.setTextColor(resources.getColor(textColor, null))
            binding.tvAlertMessage.text =
                "${alert.description}\n${
                    if (alert.distanceKm >= 0)
                        "~${String.format("%.1f", alert.distanceKm)} km from you"
                    else
                        "Distance unavailable"
                }"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
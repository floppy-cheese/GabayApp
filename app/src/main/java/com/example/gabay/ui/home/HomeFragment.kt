package com.example.gabay.ui.home

import android.content.Intent
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

    private var hasCompletedFirstLiveFetch = false

    private fun extractMagnitude(description: String): Double {
        val regex = Regex("""Magnitude (\d+\.\d+)""")
        return regex.find(description)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun extractLocation(description: String): String {
        return description.lines().firstOrNull()
            ?.replace(Regex("""^Magnitude \d+\.\d+ — """), "")
            ?: "Unknown location"
    }

    private fun extractTime(description: String): String {
        return description.lines().getOrNull(1) ?: ""
    }

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

//        binding.btnTestAlert.setOnClickListener {
//            val intent = Intent(requireContext(), FullScreenAlertActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                putExtra("disaster_type", "EARTHQUAKE")
//                putExtra("magnitude", 6.2)
//                putExtra("location", "Tingloy, Batangas")
//                putExtra("distance", 45.0)
//                putExtra("reportedTime", "Reported on: Apr 06, 2026 at 03:14 PM")
//                putExtra("actionText", "EVACUATE NOW")
//            }
//            startActivity(intent)
//        }
//
//        binding.btnTestTyphoon.setOnClickListener {
//            val intent = Intent(requireContext(), FullScreenAlertActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                putExtra("disaster_type", "TYPHOON")
//                putExtra("location", "Typhoon DANIKA")
//                putExtra("signal_no", "Signal No. 2")
//                putExtra("wind_speed", 120)
//                putExtra("distance", 150.0)
//                putExtra("reportedTime", "Updated: Apr 29, 2026 at 06:00 PM")
//                putExtra("actionText", "PREPARE TO EVACUATE")
//            }
//            startActivity(intent)
//        }
//
//        binding.btnTestNotification.setOnClickListener {
//            val mockAlert = DisasterAlert(
//                id = "notif_test",
//                type = com.example.gabay.data.models.DisasterType.TYPHOON,
//                description = "Typhoon DANIKA — Signal No. 2\nMax winds: 120 km/h\nUpdated: Apr 29, 2026 at 06:00 PM",
//                distanceKm = 150.0,
//                severity = Severity.HIGH,
//                isActive = true
//            )
//            AlertNotificationManager.sendDisasterNotification(requireContext(), mockAlert)
//        }
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

                    if (hasCompletedFirstLiveFetch &&
                        (mostSevere.severity == Severity.HIGH ||
                                mostSevere.severity == Severity.CRITICAL)
                    ) {
                        val isTyphoon = mostSevere.type == com.example.gabay.data.models.DisasterType.TYPHOON
                        val intent = Intent(requireContext(), FullScreenAlertActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("disaster_type", if (isTyphoon) "TYPHOON" else "EARTHQUAKE")
                            if (isTyphoon) {
                                putExtra("location", mostSevere.description.lines().getOrNull(0) ?: "")
                                putExtra("signal_no", mostSevere.description.lines().getOrNull(0)
                                    ?.substringAfter("—")?.trim() ?: "")
                                putExtra("wind_speed", Regex("""(\d+) km/h""")
                                    .find(mostSevere.description)?.groupValues?.get(1)?.toIntOrNull() ?: 0)
                            } else {
                                putExtra("magnitude", extractMagnitude(mostSevere.description))
                                putExtra("location", extractLocation(mostSevere.description))
                            }
                            putExtra("distance", mostSevere.distanceKm)
                            putExtra("reportedTime", extractTime(mostSevere.description))
                            putExtra("actionText", if (mostSevere.severity == Severity.CRITICAL)
                                "EVACUATE NOW" else "PREPARE TO EVACUATE")
                        }
                        startActivity(intent)
                        AlertNotificationManager.sendDisasterNotification(requireContext(), mostSevere)
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

            val typeIcon = when (alert.type) {
                com.example.gabay.data.models.DisasterType.TYPHOON    -> "🌀"
                com.example.gabay.data.models.DisasterType.EARTHQUAKE -> "🌍"
                else -> ""
            }

            binding.cardDisasterAlert.setCardBackgroundColor(
                resources.getColor(bgColor, null)
            )
            binding.tvAlertTitle.setTextColor(resources.getColor(textColor, null))
            binding.tvAlertTitle.text = "$typeIcon $emoji $actionText"
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
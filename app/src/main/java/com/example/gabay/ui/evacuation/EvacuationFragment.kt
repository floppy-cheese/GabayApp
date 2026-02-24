package com.example.gabay.ui.evacuation

import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.gabay.R
import com.example.gabay.databinding.FragmentEvacuationBinding
import com.example.gabay.data.repository.LocationRepository
import com.example.gabay.data.repository.EvacuationRepository
import com.example.gabay.data.models.TravelMode
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class EvacuationFragment : Fragment() {

    private var _binding: FragmentEvacuationBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: EvacuationViewModel

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var destinationMarker: Marker? = null
    private var routeLine: Polyline? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEvacuationBinding.inflate(inflater, container, false)

        // Initialize OSMDroid configuration
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val locationRepo = LocationRepository(requireContext())
        val evacuationRepo = EvacuationRepository(requireContext())
        val factory = EvacuationViewModelFactory(locationRepo, evacuationRepo)
        viewModel = ViewModelProvider(this, factory)[EvacuationViewModel::class.java]

        setupMap()
        setupUI()
        observeViewModel()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)

            // Set default zoom to Philippines/Calamba area
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(14.2108, 121.1649)) // Calamba center

            // Enable user location
            myLocationOverlay = MyLocationNewOverlay(
                GpsMyLocationProvider(requireContext()),
                this
            ).apply {
                enableMyLocation()
                enableFollowLocation()
            }
            overlays.add(myLocationOverlay)
        }
    }

    private fun setupUI() {
        binding.btnSafeYes.setOnClickListener {
            binding.cardSafetyCheck.visibility = View.GONE
            binding.cardTravelMode.visibility = View.VISIBLE
        }

        binding.btnSafeNo.setOnClickListener {
            Snackbar.make(
                binding.root,
                "Please wait for safe conditions. DROP, COVER, and HOLD ON during earthquakes!",
                Snackbar.LENGTH_LONG
            ).show()
        }

        // Listen to radio button changes
        binding.radioGroupTravel.setOnCheckedChangeListener { _, checkedId ->
            // Clear previous route when travel mode changes
            viewModel.clearRoute()
        }

        binding.btnFindCenter.setOnClickListener {
            val travelMode = if (binding.radioWalking.isChecked) {
                TravelMode.WALKING
            } else {
                TravelMode.VEHICLE
            }
            viewModel.findNearestCenter(travelMode)
        }

        binding.btnBackToSafety.setOnClickListener {
            hideMap()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show loading state (you can add a progress bar if needed)
        }

        viewModel.nearestCenter.observe(viewLifecycleOwner) { center ->
            if (_binding != null && center != null) {
                binding.tvCenterName.text = center.name
                binding.tvCenterAddress.text = center.address
            }
        }

        viewModel.routeInfo.observe(viewLifecycleOwner) { route ->
            if (_binding != null && route != null) {
                binding.tvDistance.text = String.format("%.2f km", route.distanceKm)
                binding.tvTime.text = "${route.estimatedTimeMinutes} min"

                // Show map with route
                showMapWithRoute(route)
            }
        }

        viewModel.currentSpeed.observe(viewLifecycleOwner) { speed ->
            if (_binding != null) {
                val display = if (speed > 0.5) String.format("%.1f km/h", speed) else "-- km/h"
                binding.tvSpeed.text = display
            }
        }

        viewModel.remainingDistance.observe(viewLifecycleOwner) { distance ->
            if (_binding != null && distance != null) {
                binding.tvDistance.text = String.format("%.2f km", distance)
            }
        }

        viewModel.updatedETA.observe(viewLifecycleOwner) { eta ->
            if (_binding != null && eta != null) {
                binding.tvTime.text = "$eta min"
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (_binding != null) {
                error?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            if (_binding != null && location != null) {
                val userPoint = GeoPoint(location.latitude, location.longitude)
                myLocationOverlay?.let {
                    binding.mapView.controller.animateTo(userPoint)
                }
            }
        }
    }

    private fun showMapWithRoute(route: com.example.gabay.data.models.RouteInfo) {
        // Hide safety screens, show map
        binding.scrollSafetyScreen.visibility = View.GONE
        binding.mapView.visibility = View.VISIBLE
        binding.cardBottomInfo.visibility = View.VISIBLE

        // Clear previous overlays (except location)
        destinationMarker?.let { binding.mapView.overlays.remove(it) }
        routeLine?.let { binding.mapView.overlays.remove(it) }

        // Add destination marker
        val destination = route.waypoints.lastOrNull()
        destination?.let {
            val destPoint = GeoPoint(it.latitude, it.longitude)

            destinationMarker = Marker(binding.mapView).apply {
                position = destPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Evacuation Center"
                snippet = "Your destination"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_evacuation_marker)
            }
            binding.mapView.overlays.add(destinationMarker)
        }

        // Draw route line - FIXED
        if (route.waypoints.size >= 2) {
            val routePoints = route.waypoints.map {
                GeoPoint(it.latitude, it.longitude)
            }

            routeLine = Polyline(binding.mapView).apply {
                setPoints(routePoints)
                outlinePaint.color = Color.BLUE
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            }
            binding.mapView.overlays.add(0, routeLine) // Add at index 0 so it's below markers
        }

        // Center map to show route
        val startPoint = route.waypoints.firstOrNull()?.let {
            GeoPoint(it.latitude, it.longitude)
        }
        startPoint?.let {
            binding.mapView.controller.animateTo(it)
            binding.mapView.controller.setZoom(14.5)
        }

        binding.mapView.invalidate()

        // Start real-time ETA updates
        viewModel.startETATracking()
    }

    private fun hideMap() {
        // Stop ETA tracking
        viewModel.stopETATracking()

        binding.mapView.visibility = View.GONE
        binding.cardBottomInfo.visibility = View.GONE
        binding.scrollSafetyScreen.visibility = View.VISIBLE
        binding.cardSafetyCheck.visibility = View.VISIBLE
        binding.cardTravelMode.visibility = View.GONE

        // Clear map overlays
        destinationMarker?.let { binding.mapView.overlays.remove(it) }
        routeLine?.let { binding.mapView.overlays.remove(it) }
        binding.mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDetach()
        myLocationOverlay?.disableMyLocation()
        myLocationOverlay = null
        destinationMarker = null
        routeLine = null
        _binding = null
    }
}
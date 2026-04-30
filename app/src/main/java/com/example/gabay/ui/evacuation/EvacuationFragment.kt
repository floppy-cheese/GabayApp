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
import androidx.lifecycle.lifecycleScope
import com.example.gabay.R
import com.example.gabay.databinding.FragmentEvacuationBinding
import com.example.gabay.data.repository.LocationRepository
import com.example.gabay.data.repository.EvacuationRepository
import com.example.gabay.data.models.TravelMode
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
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
    private var faultLineOverlay: FaultLineOverlay? = null  // ← NEW

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEvacuationBinding.inflate(inflater, container, false)
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // THIS MUST BE FIRST — before anything else
        val locationRepo = LocationRepository(requireContext())
        val evacuationRepo = EvacuationRepository(requireContext())
        val factory = EvacuationViewModelFactory(locationRepo, evacuationRepo)
        viewModel = ViewModelProvider(this, factory)[EvacuationViewModel::class.java]

        // Only NOW is it safe to use viewModel
        val skipSafetyCheck = arguments?.getBoolean("skip_safety_check", false) ?: false
        val autoWalking = arguments?.getBoolean("auto_travel_mode_walking", true) ?: true

        if (skipSafetyCheck) {
            binding.cardSafetyCheck.visibility = View.GONE
            binding.cardTravelMode.visibility = View.VISIBLE
            if (autoWalking) binding.radioWalking.isChecked = true
            else binding.radioVehicle.isChecked = true

            // Wait for first location fix before auto-triggering route
            val travelMode = if (autoWalking) TravelMode.WALKING else TravelMode.VEHICLE
            viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
                if (location != null && viewModel.routeInfo.value == null) {
                    viewModel.findNearestCenter(travelMode)
                    // Jump straight to map — skip the "Show Map and Route" button
                }
            }
        }

        setupMap()
        setupUI()
        observeViewModel()
    }

    private fun setupMap() {
        // Pre-load fault data in background immediately
        faultLineOverlay = FaultLineOverlay(requireContext(), binding.mapView, lifecycleScope)
        faultLineOverlay?.load()

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(14.2108, 121.1649))

            myLocationOverlay = MyLocationNewOverlay(
                GpsMyLocationProvider(requireContext()), this
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

        binding.radioGroupTravel.setOnCheckedChangeListener { _, _ ->
            viewModel.clearRoute()
        }

        binding.btnFindCenter.setOnClickListener {
            val travelMode = if (binding.radioWalking.isChecked) TravelMode.WALKING else TravelMode.VEHICLE
            viewModel.findNearestCenter(travelMode)
        }

        binding.btnBackToSafety.setOnClickListener { hideMap() }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { }

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
                error?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
            }
        }

        viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            if (_binding != null && location != null) {
                val userPoint = GeoPoint(location.latitude, location.longitude)
                myLocationOverlay?.let { binding.mapView.controller.animateTo(userPoint) }
            }
        }
    }

    private fun showMapWithRoute(route: com.example.gabay.data.models.RouteInfo) {
        binding.scrollSafetyScreen.visibility = View.GONE
        binding.mapView.visibility = View.VISIBLE
        binding.cardBottomInfo.visibility = View.VISIBLE

        destinationMarker?.let { binding.mapView.overlays.remove(it) }
        routeLine?.let { binding.mapView.overlays.remove(it) }

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

        if (route.waypoints.size >= 2) {
            val routePoints = route.waypoints.map { GeoPoint(it.latitude, it.longitude) }
            routeLine = Polyline(binding.mapView).apply {
                setPoints(routePoints)
                outlinePaint.color = Color.BLUE
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            }
            // Route at index 0 (bottom), fault lines will be above it, markers on top
            binding.mapView.overlays.add(0, routeLine)
        }

        val startPoint = route.waypoints.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
        startPoint?.let {
            binding.mapView.controller.animateTo(it)
            binding.mapView.controller.setZoom(14.5)
        }

        binding.mapView.invalidate()

        viewModel.startETATracking()
    }

    private fun hideMap() {
        viewModel.stopETATracking()
        binding.mapView.visibility = View.GONE
        binding.cardBottomInfo.visibility = View.GONE
        binding.scrollSafetyScreen.visibility = View.VISIBLE
        binding.cardSafetyCheck.visibility = View.VISIBLE
        binding.cardTravelMode.visibility = View.GONE

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
        faultLineOverlay?.clear()
        faultLineOverlay = null
        binding.mapView.onDetach()
        myLocationOverlay?.disableMyLocation()
        myLocationOverlay = null
        destinationMarker = null
        routeLine = null
        _binding = null
    }
}
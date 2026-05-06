package com.example.gabay.ui.evacuation

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import com.example.gabay.data.models.Maneuver
import com.example.gabay.data.models.RouteInfo
import com.example.gabay.data.models.TravelMode
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import kotlin.math.*

class EvacuationFragment : Fragment() {

    private var _binding: FragmentEvacuationBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: EvacuationViewModel

    // ── Map overlays ──────────────────────────────────────────────────────────

    private var userMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var routeLine: Polyline? = null
    private var faultLineOverlay: FaultLineOverlay? = null

    // ── Navigation state ──────────────────────────────────────────────────────

    private var activeRoute: RouteInfo? = null
    private var currentManeuverIndex = 0
    private var isCameraLocked = true
    private var lastKnownLocation: Location? = null
    private var lastKnownGeoPoint: GeoPoint? = null

    // ── Loading / launch state ────────────────────────────────────────────────

    private var isAutoLaunch = false
    private var hasFirstFix  = false
    // Set to true BEFORE findNearestCenter() is called so the observer
    // can't double-fire a second routing job while the first is running.
    private var isCalculatingRoute = false

    // ── Location ──────────────────────────────────────────────────────────────

    private var locationManager: LocationManager? = null

    private val locationListener = LocationListener { location ->
        lastKnownLocation = location
        lastKnownGeoPoint = GeoPoint(location.latitude, location.longitude)

        if (isAutoLaunch && !hasFirstFix) {
            hasFirstFix = true
            setLoadingStatus("Calculating route…")
        }

        onLocationUpdate(location)
    }

    // ── Sensors ───────────────────────────────────────────────────────────────

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val rotationMatrix    = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val gravity           = FloatArray(3)
    private val geomagnetic       = FloatArray(3)
    private var lastBearing       = 0f

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    applyBearing(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
                }
                Sensor.TYPE_ACCELEROMETER ->
                    System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        applyBearing(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
                    }
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEvacuationBinding.inflate(inflater, container, false)
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val locationRepo   = LocationRepository(requireContext())
        val evacuationRepo = EvacuationRepository(requireContext())
        viewModel = ViewModelProvider(this, EvacuationViewModelFactory(locationRepo, evacuationRepo))[EvacuationViewModel::class.java]

        val skipSafetyCheck = arguments?.getBoolean("skip_safety_check", false) ?: false
        val autoWalking     = arguments?.getBoolean("auto_travel_mode_walking", true) ?: true

        if (skipSafetyCheck) {
            isAutoLaunch = true
            binding.scrollSafetyScreen.visibility = View.GONE
            showLoadingOverlay("Finding your location…")

            val travelMode = if (autoWalking) TravelMode.WALKING else TravelMode.VEHICLE

            // Single-shot observer: fires once when we have a location and no route yet.
            // isCalculatingRoute is set synchronously so a second emission can't double-fire.
            viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
                if (location != null && viewModel.routeInfo.value == null && !isCalculatingRoute && !viewModel.isRoutingActive) {
                    isCalculatingRoute = true
                    setLoadingStatus("Calculating route…")
                    viewModel.findNearestCenter(travelMode)
                }
            }
        }

        setupSensors()
        setupLocationManager()
        setupMap()
        setupUI()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        registerSensors()
        requestLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        sensorManager?.unregisterListener(sensorListener)
        locationManager?.removeUpdates(locationListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        faultLineOverlay?.clear()
        faultLineOverlay  = null
        binding.mapView.onDetach()
        userMarker        = null
        destinationMarker = null
        routeLine         = null
        _binding          = null
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupSensors() {
        sensorManager  = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer  = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    private fun registerSensors() {
        rotationSensor?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            accelerometer?.let { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let  { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    private fun setupLocationManager() {
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @Suppress("MissingPermission")
    private fun requestLocationUpdates() {
        val hasGps     = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val hasNetwork = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        if (hasGps)     locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,     1000L, 5f, locationListener)
        if (hasNetwork) locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, locationListener)
    }

    private fun setupMap() {
        faultLineOverlay = FaultLineOverlay(requireContext(), binding.mapView, lifecycleScope)
        faultLineOverlay?.load()

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(14.2108, 121.1649))
            val rotationOverlay = RotationGestureOverlay(this)
            rotationOverlay.isEnabled = true
            overlays.add(rotationOverlay)
        }

        binding.fabRecenter.setOnClickListener {
            isCameraLocked = true
            binding.fabRecenter.hide()
            binding.mapView.mapOrientation = -lastBearing
            lastKnownGeoPoint?.let { point ->
                binding.mapView.controller.setZoom(18.0)
                binding.mapView.controller.animateTo(point)
            }
        }
    }

    private var navigationListenersAttached = false

    private fun attachNavigationMapListeners() {
        if (navigationListenersAttached) return
        navigationListenersAttached = true
        binding.mapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                if (isCameraLocked) {
                    isCameraLocked = false
                    binding.fabRecenter.show()
                }
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?) = false
        })
    }

    private fun setupUI() {
        binding.btnSafeYes.setOnClickListener {
            binding.cardSafetyCheck.visibility = View.GONE
            binding.cardTravelMode.visibility  = View.VISIBLE
        }
        binding.btnSafeNo.setOnClickListener {
            Snackbar.make(binding.root,
                "Please wait for safe conditions. DROP, COVER, and HOLD ON during earthquakes!",
                Snackbar.LENGTH_LONG).show()
        }
        binding.radioGroupTravel.setOnCheckedChangeListener { _, _ -> viewModel.clearRoute() }

        // ── "Show Map and Route" button ───────────────────────────────────────
        // Disabled immediately on first tap; re-enabled if routing fails.
        binding.btnFindCenter.setOnClickListener {
            if (viewModel.isRoutingActive) return@setOnClickListener   // hard guard
            binding.btnFindCenter.isEnabled = false
            val mode = if (binding.radioWalking.isChecked) TravelMode.WALKING else TravelMode.VEHICLE
            viewModel.findNearestCenter(mode)
        }

        binding.btnBackToSafety.setOnClickListener { hideMap() }

        // ── "Path Blocked?" reroute button ───────────────────────────────────
        binding.btnReroute.setOnClickListener {
            val mode = viewModel.currentLocation.value?.let {
                if (binding.radioWalking.isChecked) TravelMode.WALKING else TravelMode.VEHICLE
            } ?: TravelMode.WALKING
            viewModel.switchToNextCenter(mode)
        }
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.nearestCenter.observe(viewLifecycleOwner) { center ->
            if (_binding != null && center != null) {
                binding.tvCenterName.text    = center.name
                binding.tvCenterAddress.text = center.address
            }
        }

        viewModel.routeInfo.observe(viewLifecycleOwner) { route ->
            if (_binding == null || route == null) return@observe
            hideLoadingOverlay()
            isCalculatingRoute       = false           // allow re-trigger if needed
            binding.btnFindCenter.isEnabled = true     // re-enable for manual re-search
            binding.tvDistance.text  = String.format("%.2f km", route.distanceKm)
            binding.tvTime.text      = "${route.estimatedTimeMinutes} min"
            showMapWithRoute(route)
        }

        viewModel.currentSpeed.observe(viewLifecycleOwner) { speed ->
            if (_binding != null) binding.tvSpeed.text =
                if (speed > 0.5) String.format("%.1f km/h", speed) else "-- km/h"
        }

        viewModel.remainingDistance.observe(viewLifecycleOwner) { distance ->
            if (_binding != null && distance != null)
                binding.tvDistance.text = String.format("%.2f km", distance)
        }

        viewModel.updatedETA.observe(viewLifecycleOwner) { eta ->
            if (_binding != null && eta != null) binding.tvTime.text = "$eta min"
        }

        // Disable "Path Blocked?" when no alternates remain
        viewModel.alternatesRemaining.observe(viewLifecycleOwner) { remaining ->
            if (_binding != null) {
                binding.btnReroute.isEnabled = (remaining ?: 0) > 0
                binding.btnReroute.alpha     = if ((remaining ?: 0) > 0) 1.0f else 0.4f
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (_binding != null) {
                error?.let {
                    hideLoadingOverlay()
                    isCalculatingRoute       = false
                    binding.btnFindCenter.isEnabled = true
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Loading overlay ───────────────────────────────────────────────────────

    private fun showLoadingOverlay(status: String) {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.tvLoadingStatus.text      = status
    }

    private fun setLoadingStatus(status: String) {
        if (_binding != null) binding.tvLoadingStatus.text = status
    }

    private fun hideLoadingOverlay() {
        if (_binding != null) binding.loadingOverlay.visibility = View.GONE
    }

    // ── Location updates ──────────────────────────────────────────────────────

    /**
     * Called on every GPS tick (every ~1 s while moving).
     *
     * Camera policy:
     * - Only animate the camera if the user has moved at least 15 m since the last
     *   camera update. This prevents jitter when standing still or moving slowly
     *   (where sub-meter GPS noise was causing constant re-centering).
     * - When moving fast (speed > 5 km/h from GPS) we use setCenter (instant snap)
     *   instead of animateTo so the animation can't lag behind the vehicle.
     */
    private var lastCameraGeoPoint: GeoPoint? = null
    private val CAMERA_MOVE_THRESHOLD_M = 15f   // only update camera if moved this far

    private fun onLocationUpdate(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)
        updateUserMarker(point)

        val speedKmh = location.speed * 3.6f
        if (speedKmh >= 1f && location.hasBearing()) applyBearing(location.bearing)

        if (isCameraLocked && binding.mapContainer.visibility == View.VISIBLE) {
            val lastCam = lastCameraGeoPoint
            val movedEnough = lastCam == null ||
                    lastKnownLocation?.distanceTo(
                        android.location.Location("").also {
                            it.latitude  = lastCam.latitude
                            it.longitude = lastCam.longitude
                        }
                    )?.let { it >= CAMERA_MOVE_THRESHOLD_M } ?: true

            if (movedEnough) {
                lastCameraGeoPoint = point
                if (speedKmh > 5f) {
                    // Fast movement → instant snap, no animation lag
                    binding.mapView.controller.setZoom(18.0)
                    binding.mapView.controller.setCenter(point)
                } else {
                    // Slow / stationary → smooth animate
                    binding.mapView.controller.setZoom(18.0)
                    binding.mapView.controller.animateTo(point)
                }
            }
        }

        advanceManeuverIfNeeded(location.latitude, location.longitude)
    }

    private fun updateUserMarker(point: GeoPoint) {
        if (userMarker == null) {
            userMarker = Marker(binding.mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon  = ContextCompat.getDrawable(requireContext(), R.drawable.ic_user_location)
                title = "You are here"
            }
            binding.mapView.overlays.add(userMarker)
        }
        userMarker?.position = point
        binding.mapView.invalidate()
    }

    // ── Bearing / compass ─────────────────────────────────────────────────────

    private fun applyBearing(rawBearing: Float) {
        val normalized = ((rawBearing % 360) + 360) % 360
        lastBearing    = lastBearing + 0.15f * shortestAngularDelta(lastBearing, normalized)
        if (isCameraLocked) binding.mapView.mapOrientation = -lastBearing
    }

    private fun shortestAngularDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d > 180f)  d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    // ── Turn-by-turn ──────────────────────────────────────────────────────────

    private fun advanceManeuverIfNeeded(userLat: Double, userLon: Double) {
        val route    = activeRoute ?: return
        val maneuver = route.maneuvers.getOrNull(currentManeuverIndex) ?: return
        val next     = route.maneuvers.getOrNull(currentManeuverIndex + 1)

        val turnPoint  = route.waypoints.getOrNull(maneuver.beginWaypointIndex) ?: return
        val distToTurn = haversineKm(userLat, userLon, turnPoint.latitude, turnPoint.longitude)

        updateBanner(maneuver, distToTurn)
        if (distToTurn < 0.03 && next != null) currentManeuverIndex++
    }

    private fun updateBanner(maneuver: Maneuver, distToTurnKm: Double) {
        binding.bannerContainer.visibility = View.VISIBLE
        binding.tvInstruction.text         = maneuver.instruction
        binding.tvNextDistance.text        = formatDistance(distToTurnKm)
    }

    private fun formatDistance(km: Double): String = when {
        km < 0.05 -> "Turn now"
        km < 1.0  -> "In ${(km * 1000).roundToInt()} m"
        else      -> "In ${"%.1f".format(km)} km"
    }

    // ── Map display ───────────────────────────────────────────────────────────

    private fun showMapWithRoute(route: RouteInfo) {
        activeRoute          = route
        currentManeuverIndex = 0
        isCameraLocked       = true
        lastCameraGeoPoint   = null     // reset so camera moves immediately on first tick
        binding.fabRecenter.hide()

        attachNavigationMapListeners()

        binding.scrollSafetyScreen.visibility = View.GONE
        binding.mapContainer.visibility       = View.VISIBLE
        binding.cardBottomInfo.visibility     = View.VISIBLE

        // Banner: always show if maneuvers exist (fallback now always has them)
        binding.bannerContainer.visibility =
            if (route.maneuvers.isNotEmpty()) View.VISIBLE else View.GONE

        // ── Remove old overlays (in-place reroute keeps the map visible) ──────
        destinationMarker?.let { binding.mapView.overlays.remove(it) }
        routeLine?.let         { binding.mapView.overlays.remove(it) }

        // ── New destination marker ─────────────────────────────────────────────
        route.waypoints.lastOrNull()?.let {
            destinationMarker = Marker(binding.mapView).apply {
                position = GeoPoint(it.latitude, it.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title   = "Evacuation Center"
                snippet = "Your destination"
                icon    = ContextCompat.getDrawable(requireContext(), R.drawable.ic_evacuation_marker)
            }
            binding.mapView.overlays.add(destinationMarker)
        }

        // ── New route polyline ─────────────────────────────────────────────────
        if (route.waypoints.size >= 2) {
            routeLine = Polyline(binding.mapView).apply {
                setPoints(route.waypoints.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.color       = Color.BLUE
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap   = android.graphics.Paint.Cap.ROUND
            }
            binding.mapView.overlays.add(0, routeLine)
        }

        // ── Camera ────────────────────────────────────────────────────────────
        val centerPoint = lastKnownGeoPoint ?: route.waypoints.firstOrNull()
            ?.let { GeoPoint(it.latitude, it.longitude) }
        centerPoint?.let {
            binding.mapView.controller.setZoom(18.0)
            binding.mapView.controller.animateTo(it)
            binding.mapView.mapOrientation = -lastBearing
        }

        binding.mapView.invalidate()
        viewModel.startETATracking()

        // Seed the banner immediately without waiting for next GPS tick
        lastKnownLocation?.let {
            advanceManeuverIfNeeded(it.latitude, it.longitude)
            onLocationUpdate(it)
        }
    }

    private fun hideMap() {
        viewModel.stopETATracking()
        viewModel.clearRoute()
        activeRoute                  = null
        isCameraLocked               = true
        isCalculatingRoute           = false
        navigationListenersAttached  = false
        lastBearing                  = 0f
        lastCameraGeoPoint           = null

        binding.fabRecenter.hide()
        binding.loadingOverlay.visibility     = View.GONE
        binding.bannerContainer.visibility    = View.GONE
        binding.mapContainer.visibility       = View.GONE
        binding.cardBottomInfo.visibility     = View.GONE
        binding.scrollSafetyScreen.visibility = View.VISIBLE
        binding.cardSafetyCheck.visibility    = View.VISIBLE
        binding.cardTravelMode.visibility     = View.GONE
        binding.btnFindCenter.isEnabled       = true

        destinationMarker?.let { binding.mapView.overlays.remove(it) }
        routeLine?.let         { binding.mapView.overlays.remove(it) }
        binding.mapView.mapOrientation = 0f
        binding.mapView.invalidate()
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
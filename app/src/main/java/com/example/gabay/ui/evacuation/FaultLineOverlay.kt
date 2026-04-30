package com.example.gabay.ui.evacuation

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class FaultLineOverlay(
    private val context: Context,
    private val mapView: MapView,
    private val scope: CoroutineScope
) {
    companion object {
        private const val ASSET_FILE = "philippine_fault_lines.geojson"
    }

    private val activeOverlays = mutableListOf<Polyline>()
    private var faultData: List<FaultFeature> = emptyList()
    private var isLoaded = false
    private var isDrawn = false

    data class FaultFeature(
        val name: String,
        val note: String,
        val points: List<GeoPoint>
    )

    /** Call once on fragment start — loads and draws immediately, no zoom gating. */
    fun load() {
        if (isLoaded) return
        scope.launch {
            faultData = withContext(Dispatchers.IO) { parseFaultLines() }
            isLoaded = true
            withContext(Dispatchers.Main) { drawFaultLines() }
        }
    }

    private fun drawFaultLines() {
        if (isDrawn) return
        faultData.forEach { fault ->
            val line = Polyline(mapView).apply {
                setPoints(fault.points)
                outlinePaint.apply {
                    color = Color.parseColor("#CC0000")
                    strokeWidth = 10f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    alpha = 180
                }
                title = fault.name
                snippet = fault.note
                setOnClickListener { _, _, _ ->
                    infoWindow?.open(this, points[points.size / 2], 0, 0)
                    true
                }
            }
            mapView.overlays.add(line)
            activeOverlays.add(line)
        }
        isDrawn = true
        mapView.invalidate()
    }

    fun clear() {
        activeOverlays.forEach { mapView.overlays.remove(it) }
        activeOverlays.clear()
        isDrawn = false
        mapView.invalidate()
    }

    private fun parseFaultLines(): List<FaultFeature> {
        return try {
            val json = context.assets.open(ASSET_FILE).bufferedReader().readText()
            val root = JSONObject(json)
            val features = root.getJSONArray("features")
            val result = mutableListOf<FaultFeature>()

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val geometry = feature.getJSONObject("geometry")
                val geomType = geometry.getString("type")

                val name = props.optString("name", "Fault Line")
                val note = props.optString("note", "")
                val coordsArray = geometry.getJSONArray("coordinates")

                val points = mutableListOf<GeoPoint>()

                when (geomType) {
                    "LineString" -> {
                        for (j in 0 until coordsArray.length()) {
                            val pair = coordsArray.getJSONArray(j)
                            points.add(GeoPoint(pair.getDouble(1), pair.getDouble(0)))
                        }
                    }
                    "MultiLineString" -> {
                        // Flatten all segments into one continuous list of points
                        for (s in 0 until coordsArray.length()) {
                            val segment = coordsArray.getJSONArray(s)
                            for (j in 0 until segment.length()) {
                                val pair = segment.getJSONArray(j)
                                points.add(GeoPoint(pair.getDouble(1), pair.getDouble(0)))
                            }
                        }
                    }
                }

                if (points.size >= 2) result.add(FaultFeature(name, note, points))
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("FaultLineOverlay", "Error parsing fault lines: ${e.message}")
            emptyList()
        }
    }
}
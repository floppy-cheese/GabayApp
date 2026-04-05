package com.example.gabay.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface EarthquakeApiService {
    @GET("query")
    suspend fun getPhilippineEarthquakes(
        @Query("format") format: String = "geojson",
        @Query("minlatitude") minLat: Double = 4.5,
        @Query("maxlatitude") maxLat: Double = 21.5,
        @Query("minlongitude") minLon: Double = 116.0,
        @Query("maxlongitude") maxLon: Double = 127.0,
        @Query("minmagnitude") minMag: Double = 2.5,
        @Query("orderby") orderBy: String = "time",
        @Query("limit") limit: Int = 5
    ): EarthquakeResponse
}

data class EarthquakeResponse(
    @SerializedName("features") val features: List<EarthquakeFeature>
)

data class EarthquakeFeature(
    @SerializedName("properties") val properties: EarthquakeProperties,
    @SerializedName("geometry") val geometry: EarthquakeGeometry
)

data class EarthquakeProperties(
    @SerializedName("mag") val magnitude: Double?,
    @SerializedName("place") val place: String?,
    @SerializedName("time") val time: Long?,
    @SerializedName("alert") val alert: String?
)

data class EarthquakeGeometry(
    @SerializedName("coordinates") val coordinates: List<Double>
)
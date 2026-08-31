package com.ddelpero.ridebridge.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class GeoHit(val label: String, val lat: Double, val lng: Double)

object PlaceSearch {
    private const val TAG = "PlaceSearch"
    private const val USER_AGENT = "RideBridge/1.0 (motorcycle nav; https://github.com/ddelpero)"
    private const val MAX_FUEL_METERS = 50_000f
    private const val AROUND_METERS = 25_000
    private const val FIX_WAIT_MS = 2000L

    @Volatile
    private var riderLocation: Location? = null

    fun setRiderLocation(lat: Double, lng: Double) {
        riderLocation = Location("tablet").apply {
            latitude = lat
            longitude = lng
            time = System.currentTimeMillis()
        }
        Log.d(TAG, "Rider location $lat,$lng")
    }

    fun find(context: Context, query: String): GeoHit? {
        if (isNearbyFuel(query)) {
            Log.d(TAG, "Nearby fuel deferred to tablet")
            return null
        }
        return nominatimSearch(query, bias = riderLocation) ?: geocodeFallback(context, query)
    }

    private fun isNearbyFuel(query: String): Boolean {
        val s = query.trim().lowercase()
        if (s == "gas station") return true
        return Regex("^(gas|petrol|fuel)( station)?( (near me|nearby|around here|around me))?$")
            .matches(s)
    }

    private fun overpassFuel(loc: Location): GeoHit? {
        val q = """
            [out:json][timeout:12];
            (
              node["amenity"="fuel"](around:$AROUND_METERS,${loc.latitude},${loc.longitude});
              way["amenity"="fuel"](around:$AROUND_METERS,${loc.latitude},${loc.longitude});
            );
            out center 1;
        """.trimIndent()
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://overpass-api.de/api/interpreter")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            val body = "data=" + URLEncoder.encode(q, Charsets.UTF_8.name())
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) {
                Log.w(TAG, "Overpass HTTP ${conn.responseCode}")
                return null
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val elements = json.optJSONArray("elements") ?: return null
            if (elements.length() == 0) return null
            val el = elements.getJSONObject(0)
            val lat = el.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
                ?: el.optJSONObject("center")?.optDouble("lat")
                ?: return null
            val lng = el.optDouble("lon", Double.NaN).takeIf { !it.isNaN() }
                ?: el.optJSONObject("center")?.optDouble("lon")
                ?: return null
            val name = el.optJSONObject("tags")?.optString("name").orEmpty()
                .ifBlank { "Gas station" }
            Log.d(TAG, "Overpass fuel $name $lat,$lng")
            GeoHit(name, lat, lng)
        } catch (e: Exception) {
            Log.e(TAG, "Overpass failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun nominatimNearbyFuel(loc: Location): GeoHit? {
        val d = 0.25
        val uri = Uri.parse("https://nominatim.openstreetmap.org/search").buildUpon()
            .appendQueryParameter("q", "fuel")
            .appendQueryParameter("format", "json")
            .appendQueryParameter("limit", "1")
            .appendQueryParameter("bounded", "1")
            .appendQueryParameter(
                "viewbox",
                "${loc.longitude - d},${loc.latitude + d},${loc.longitude + d},${loc.latitude - d}"
            )
            .build()
        return fetchNominatim(uri)
    }

    private fun nominatimSearch(query: String, bias: Location?): GeoHit? {
        val builder = Uri.parse("https://nominatim.openstreetmap.org/search").buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("limit", "1")
        if (bias != null) {
            builder.appendQueryParameter("lat", bias.latitude.toString())
            builder.appendQueryParameter("lon", bias.longitude.toString())
        }
        return fetchNominatim(builder.build())
    }

    private fun fetchNominatim(uri: Uri): GeoHit? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "Nominatim HTTP ${conn.responseCode}")
                return null
            }
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (arr.length() == 0) return null
            val obj = arr.getJSONObject(0)
            val lat = obj.optString("lat").toDoubleOrNull() ?: return null
            val lng = obj.optString("lon").toDoubleOrNull() ?: return null
            val display = obj.optString("display_name")
            val name = obj.optString("name").ifBlank {
                display.substringBefore(',').trim()
            }.ifBlank { display }
            Log.d(TAG, "Nominatim $name $lat,$lng")
            GeoHit(name, lat, lng)
        } catch (e: Exception) {
            Log.e(TAG, "Nominatim failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun geocodeFallback(context: Context, query: String): GeoHit? {
        if (!android.location.Geocoder.isPresent()) return null
        return try {
            val geocoder = android.location.Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(query, 1)
            val address = results?.firstOrNull() ?: return null
            val label = address.featureName?.takeIf { it.isNotBlank() && it != address.subThoroughfare }
                ?: query
            GeoHit(label, address.latitude, address.longitude)
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder fallback failed: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun waitForFix(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val latch = CountDownLatch(1)
        var fix: Location? = null
        val listener = LocationListener { location ->
            fix = location
            latch.countDown()
        }
        val main = Handler(Looper.getMainLooper())
        main.post {
            try {
                val providers = listOf(
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.GPS_PROVIDER,
                    LocationManager.FUSED_PROVIDER
                ).filter { provider ->
                    try {
                        lm.isProviderEnabled(provider)
                    } catch (_: Exception) {
                        false
                    }
                }
                if (providers.isEmpty()) {
                    latch.countDown()
                    return@post
                }
                providers.forEach { provider ->
                    try {
                        lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    } catch (e: Exception) {
                        Log.w(TAG, "requestLocationUpdates $provider failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "requestLocationUpdates failed: ${e.message}")
                latch.countDown()
            }
        }
        latch.await(FIX_WAIT_MS, TimeUnit.MILLISECONDS)
        main.post {
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {
            }
        }
        return fix ?: lastLocation(context)
    }

    @SuppressLint("MissingPermission")
    fun lastLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        return providers.mapNotNull { provider ->
            try {
                lm.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

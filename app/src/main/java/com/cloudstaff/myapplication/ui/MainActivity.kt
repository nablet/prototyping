package com.cloudstaff.myapplication.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cloudstaff.myapplication.R
import com.cloudstaff.myapplication.databinding.ActivityMainBinding
import com.cloudstaff.myapplication.utils.http.Http
import com.cloudstaff.myapplication.utils.http.hasInternetConnection
import com.cloudstaff.myapplication.utils.prefs.PrefsHelper
import com.cloudstaff.myapplication.utils.retrofit.Inputs
import com.cloudstaff.myapplication.utils.retrofit.Payload
import com.cloudstaff.myapplication.utils.retrofit.api
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

	private lateinit var gmap: GoogleMap
	private lateinit var fusedLocationClient: FusedLocationProviderClient
	private lateinit var prefs: PrefsHelper
	private var currentPolyline: Polyline? = null
	private var currentLatLng: LatLng? = null



	private lateinit var binding: ActivityMainBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// set token here
		Http.bearerToken = "app-RUajyaiMG0aoPHM4bWRMpNTW"

		fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

		val mapFragment = supportFragmentManager
			.findFragmentById(R.id.map) as SupportMapFragment
		mapFragment.getMapAsync(this)

		prefs = PrefsHelper(this)

		setupClickListeners()
	}

	private fun setupClickListeners() {
		binding.evacCenters.setOnClickListener {
			fetchEvacCenters()
		}
	}

	override fun onMapReady(googleMap: GoogleMap) {
		gmap = googleMap

		requestLocationPermission()

		gmap.setOnMapClickListener { latLng ->
			// latLng is where the user tapped
			Toast.makeText(this, "Clicked at: ${latLng.latitude}, ${latLng.longitude}", Toast.LENGTH_SHORT).show()

			// Example: add a marker where the user clicked
			gmap.addMarker(MarkerOptions().position(latLng).title("You clicked here"))
		}

		gmap.setOnMarkerClickListener { marker ->
			val evac = marker.tag as? Locations ?: return@setOnMarkerClickListener false
			val destination = LatLng(evac.coordinates.lat, evac.coordinates.lng)

			drawRouteTo(destination)
			marker.showInfoWindow()

			false
		}


		gmap.setOnInfoWindowClickListener { marker ->
			val center = marker.tag as? Locations
			center?.let {
				// Do something with the data
			}
		}
	}

	private val LOCATION_PERMISSION_REQUEST_CODE = 1

	private fun requestLocationPermission() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
			!= PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
				LOCATION_PERMISSION_REQUEST_CODE
			)
		} else {
			enableMyLocation()
		}
	}

	override fun onRequestPermissionsResult(
		requestCode: Int, permissions: Array<out String>, grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
			grantResults.isNotEmpty() &&
			grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			enableMyLocation()
		}
	}

	private fun enableMyLocation() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
			== PackageManager.PERMISSION_GRANTED) {
			gmap.isMyLocationEnabled = true

			fusedLocationClient.lastLocation.addOnSuccessListener { location ->
				if (location != null) {
					currentLatLng = LatLng(location.latitude, location.longitude)
					gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng ?: return@addOnSuccessListener, 15f))
				}
			}
		}
	}

	private fun fetchEvacCenters() {

		if (hasInternetConnection(this)) {
			lifecycleScope.launch {
				binding.prb.visibility = View.VISIBLE
				try {
					val payload = Payload(
						inputs = Inputs(
							barangay = "mining",
							city = "Pampanga",
							coordinate = "14.5995, 120.9842"
						),
						user = "erwinf-user-1"
					)

					val response = api.postData(payload)
					val rawArea = response.data.outputs.area
					val area = Http.json.decodeFromString<EvacCenters>(rawArea)
					val evacCenters = area.evacuation_centers

					prefs.addEvacuationCenters(evacCenters)

					addMarkers(evacCenters)

					binding.prb.visibility = View.INVISIBLE
				} catch (e: Exception) {
					binding.prb.visibility = View.INVISIBLE
					e.printStackTrace()
				}
			}

		} else {
			val evacCenters = prefs.get<List<Locations>>("evac")
			addMarkers(evacCenters)
		}
	}

	private fun addMarkers(evacCenters: List<Locations>?) {
		if (evacCenters.isNullOrEmpty()) return

		val boundsBuilder = LatLngBounds.Builder()

		evacCenters.forEach { evacCenter ->
			val position = LatLng(evacCenter.coordinates.lat, evacCenter.coordinates.lng)

			val marker = gmap.addMarker(
				MarkerOptions()
					.position(position)
					.title(evacCenter.building)
					.snippet(evacCenter.address)
			)

			marker?.tag = evacCenter
			boundsBuilder.include(position)
		}

		// Apply camera bounds after map layout is ready
		gmap.setOnMapLoadedCallback {
			val bounds = boundsBuilder.build()
			val padding = 120 // padding around edges in pixels
			gmap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
		}
	}


	suspend fun getRouteNewApi(
		origin: LatLng,
		destination: LatLng,
		apiKey: String
	): List<LatLng> = withContext(Dispatchers.IO) {

		val url = "https://routes.googleapis.com/directions/v2:computeRoutes"

		val json = """
        {
          "origin": {
            "location": {
              "latLng": { "latitude": ${origin.latitude}, "longitude": ${origin.longitude} }
            }
          },
          "destination": {
            "location": {
              "latLng": { "latitude": ${destination.latitude}, "longitude": ${destination.longitude} }
            }
          },
          "travelMode": "DRIVE"
        }
    """.trimIndent()

		val req = Request.Builder()
			.url(url)
			.addHeader("Content-Type", "application/json")
			.addHeader("X-Goog-Api-Key", apiKey)
			.addHeader("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
			.post(json.toRequestBody("application/json".toMediaType()))
			.build()

		val client = OkHttpClient()
		val response = client.newCall(req).execute()

		val result = response.body?.string() ?: return@withContext emptyList<LatLng>()

		val root = JSONObject(result)
		val routes = root.getJSONArray("routes")
		if (routes.length() == 0) return@withContext emptyList<LatLng>()

		val encoded = routes.getJSONObject(0)
			.getJSONObject("polyline")
			.getString("encodedPolyline")

		return@withContext decodePolyline(encoded)
	}


	fun decodePolyline(encoded: String): List<LatLng> {
		val poly = ArrayList<LatLng>()
		var index = 0
		val len = encoded.length
		var lat = 0
		var lng = 0

		while (index < len) {
			var b: Int
			var shift = 0
			var result = 0
			do {
				b = encoded[index++].code - 63
				result = result or (b and 0x1f shl shift)
				shift += 5
			} while (b >= 0x20)
			val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
			lat += dlat

			shift = 0
			result = 0
			do {
				b = encoded[index++].code - 63
				result = result or (b and 0x1f shl shift)
				shift += 5
			} while (b >= 0x20)
			val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
			lng += dlng

			poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
		}

		return poly
	}

	private fun drawRouteTo(destination: LatLng) {
		lifecycleScope.launch {
			val origin = currentLatLng ?: return@launch

			val points = getRouteNewApi(origin, destination, "AIzaSyAkX3ogSvySOQhOkFWzDY7HogRHx_7cbsw")
			if (points.isEmpty()) {
				Toast.makeText(this@MainActivity, "Empty route", Toast.LENGTH_SHORT).show()
				return@launch
			}

			// Remove old route
			currentPolyline?.remove()

			// Draw new route
			currentPolyline = gmap.addPolyline(
				PolylineOptions()
					.addAll(points)
					.width(12f)
					.color(Color.BLUE)
					.geodesic(true)
			)

			// Zoom camera to show both points
			val bounds = LatLngBounds.builder()
				.include(origin)
				.include(destination)
				.build()

			gmap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
		}
	}


}

@Serializable
data class DifyResponse(
	val data: DifyData,
)
@Serializable
data class DifyData(
	val outputs: Area,
)
@Serializable
data class Area(
	val area: String,
)

@Serializable
data class EvacCenters(
	val evacuation_centers: List<Locations>,
)
@Serializable
data class Locations(
	val building: String,
	val address: String,
	val coordinates: Coordinates,
	val distance_km: Double,
)
@Serializable
data class Coordinates(
	val lat: Double,
	val lng: Double,
)

//		lifecycleScope.launch {
//			@Serializable data class SampleItems(val id: String, val name: String)
//
//			val response: List<SampleItems> = Http.getJson("https://api.restful-api.dev/objects")
//
//			ListViewHelper.setup(
//				listView = binding.lvSample,
//				items = response
//			)
//		}

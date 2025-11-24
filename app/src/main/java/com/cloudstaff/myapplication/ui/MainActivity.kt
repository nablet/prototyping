package com.cloudstaff.myapplication.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
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
import com.cloudstaff.myapplication.utils.retrofit.Locations
import com.cloudstaff.myapplication.utils.retrofit.Payload
import com.cloudstaff.myapplication.utils.retrofit.PointsOfInterest
import com.cloudstaff.myapplication.utils.retrofit.api
import com.cloudstaff.myapplication.utils.toast
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

		fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

		val mapFragment = supportFragmentManager
			.findFragmentById(R.id.map) as SupportMapFragment
		mapFragment.getMapAsync(this)

		prefs = PrefsHelper(this)

		setupClickListeners()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)

		if (requestCode == 999) {
			if (resultCode == Activity.RESULT_OK) {
				enableMyLocation()
			} else {
				toast("GPS is required")
			}
		}
	}

	private fun setupClickListeners() {
		binding.evacCenters.setOnClickListener {
			fetchEvacCenters()
		}

		binding.reliefGoodsOps.setOnClickListener {
			fetchReliefGoodsOps()
		}

		binding.hospitals.setOnClickListener {
			fetchHospitals()
		}
	}

	override fun onMapReady(googleMap: GoogleMap) {
		gmap = googleMap
		gmap.uiSettings.isCompassEnabled = true
		gmap.uiSettings.isZoomControlsEnabled = true
		gmap.uiSettings.isMyLocationButtonEnabled = true
		gmap.uiSettings.isRotateGesturesEnabled = true

		requestLocationPermission()

		gmap.setOnMarkerClickListener { marker ->
			val evac = marker.tag as? Locations ?: return@setOnMarkerClickListener false
			val destination = LatLng(evac.coordinates.lat, evac.coordinates.lng)

			// Draw route
			drawRouteTo(destination)

			// Show info window
			marker.showInfoWindow()
			showLocationsBottomSheet(evac)

			// Move camera so info window is fully visible
			val offsetPixels = 150 // adjust based on info window height
			val projection = gmap.projection
			val markerPoint = projection.toScreenLocation(marker.position)
			val targetPoint = Point(markerPoint.x, markerPoint.y - offsetPixels)
			val targetLatLng = projection.fromScreenLocation(targetPoint)
			gmap.animateCamera(CameraUpdateFactory.newLatLng(targetLatLng))

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
			!= PackageManager.PERMISSION_GRANTED
		) {
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
				LOCATION_PERMISSION_REQUEST_CODE
			)
		} else {
			checkLocationSettings()
		}
	}

	override fun onRequestPermissionsResult(
		requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
			grantResults.isNotEmpty() &&
			grantResults[0] == PackageManager.PERMISSION_GRANTED
		) {
			enableMyLocation()
		}
	}

	private fun enableMyLocation() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
			== PackageManager.PERMISSION_GRANTED
		) {
			gmap.isMyLocationEnabled = true

			fusedLocationClient.lastLocation.addOnSuccessListener { location ->
				if (location != null) {
					currentLatLng = LatLng(location.latitude, location.longitude)
					gmap.animateCamera(
						CameraUpdateFactory
							.newLatLngZoom(currentLatLng ?: return@addOnSuccessListener, 16f)
					)
				} else {
					requestSingleLocation()
				}
			}
		}
	}

	@SuppressLint("MissingPermission")
	private fun requestSingleLocation() {
		val request = LocationRequest.Builder(
			Priority.PRIORITY_HIGH_ACCURACY,
			2000
		).setMaxUpdates(1).build()

		val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

		fusedLocationClient.requestLocationUpdates(
			request,
			object : LocationCallback() {
				override fun onLocationResult(result: LocationResult) {
					val location = result.lastLocation ?: return
					val myLatLng = LatLng(location.latitude, location.longitude)

					gmap.animateCamera(
						CameraUpdateFactory.newLatLngZoom(myLatLng, 16f)
					)

					fusedLocationClient.removeLocationUpdates(this)
				}
			},
			Looper.getMainLooper()
		)
	}


	private fun fetchEvacCenters() {
		if (hasInternetConnection(this)) {
			lifecycleScope.launch {
				binding.prb.visibility = View.VISIBLE
				try {
					val payload = Payload(
						inputs = Inputs(
							type = "evacuation",
							barangay = "mining",
							city = "Pampanga",
							coordinate = "14.5995, 120.9842"
						),
						user = "erwinf-user-1"
					)

					val response = api.postData(payload)
					val rawArea = response.data.outputs.area
					val area = Http.json.decodeFromString<PointsOfInterest>(rawArea)
					val evacCenters = area.nearest_evacuation_centers ?: return@launch

					prefs.addEvacuationCenters(evacCenters)

					addMarkers(evacCenters)

					binding.prb.visibility = View.INVISIBLE
				} catch (e: Exception) {
					binding.prb.visibility = View.INVISIBLE
					e.printStackTrace()
				}
			}

		} else {
			val evacCenters = prefs.getEvacuationCenters()
			addMarkers(evacCenters)
		}
	}

	private fun fetchReliefGoodsOps() {
		if (hasInternetConnection(this)) {
			lifecycleScope.launch {
				binding.prb.visibility = View.VISIBLE

				try {
					val payload = Payload(
						inputs = Inputs(
							type = "relief goods",
							barangay = "mining",
							city = "Pampanga",
							coordinate = "14.5995, 120.9842"
						),
						user = "erwinf-user-1"
					)

					val response = api.postData(payload)
					val rawArea = response.data.outputs.area
					val area = Http.json.decodeFromString<PointsOfInterest>(rawArea)
					val reliefGoods = area.relief_goods_centers ?: return@launch

					prefs.addReliefGoodsOps(reliefGoods)

					addMarkers(reliefGoods)

					binding.prb.visibility = View.INVISIBLE
				} catch (e: Exception) {
					binding.prb.visibility = View.INVISIBLE
					e.printStackTrace()
				}
			}
		} else {
			val reliefGoods = prefs.getReliefGoodsOps()
			addMarkers(reliefGoods)
		}
	}

	private fun fetchHospitals() {
		if (hasInternetConnection(this)) {
			lifecycleScope.launch {
				binding.prb.visibility = View.VISIBLE

				try {
					val payload = Payload(
						inputs = Inputs(
							type = "hospitals",
							barangay = "mining",
							city = "Pampanga",
							coordinate = "14.5995, 120.9842"
						),
						user = "erwinf-user-1"
					)

					val response = api.postData(payload)
					val rawArea = response.data.outputs.area
					val area = Http.json.decodeFromString<PointsOfInterest>(rawArea)
					val hospitals = area.hospitals ?: return@launch

					prefs.addHospitals(hospitals)

					addMarkers(hospitals)

					binding.prb.visibility = View.INVISIBLE
				} catch (e: Exception) {
					binding.prb.visibility = View.INVISIBLE
					e.printStackTrace()
				}
			}
		} else {
			val hospitals = prefs.getHospitals()
			addMarkers(hospitals)
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
		apiKey: String,
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

			val points =
				getRouteNewApi(origin, destination, "AIzaSyAkX3ogSvySOQhOkFWzDY7HogRHx_7cbsw")
			if (points.isEmpty()) {
				toast("Error in calculating route, please try again")
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

			gmap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))
		}
	}

	private fun showLocationsBottomSheet(evac: Locations) {
		val dialog = BottomSheetDialog(this)
		val view = layoutInflater.inflate(R.layout.bottom_sheet, null)

		dialog.window?.setDimAmount(0f)
		dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

		view.findViewById<TextView>(R.id.txtBuilding).text = evac.building
		view.findViewById<TextView>(R.id.txtAddress).text = evac.address
		view.findViewById<TextView>(R.id.txtDistance).text = "${evac.distance_km} km away"

		dialog.setContentView(view)
		dialog.show()
	}

	private fun checkLocationSettings() {
		val locationRequest = LocationRequest.Builder(
			Priority.PRIORITY_HIGH_ACCURACY,
			1000
		).build()

		val settingsRequest = LocationSettingsRequest.Builder()
			.addLocationRequest(locationRequest)
			.setAlwaysShow(true)   // IMPORTANT: forces the dialog to show
			.build()

		val settingsClient = LocationServices.getSettingsClient(this)

		settingsClient.checkLocationSettings(settingsRequest)
			.addOnSuccessListener {
				toast("Fetching your location...")
				enableMyLocation()
			}
			.addOnFailureListener { exception ->
				if (exception is ResolvableApiException) {
					try {
						exception.startResolutionForResult(
							this,
							999  // REQUEST_CODE
						)
					} catch (sendEx: IntentSender.SendIntentException) {
						sendEx.printStackTrace()
					}
				} else {
					toast("Location is turned off")
				}
			}
	}

}

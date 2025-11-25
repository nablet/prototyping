package com.cloudstaff.myapplication.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cloudstaff.myapplication.BuildConfig
import com.cloudstaff.myapplication.R
import com.cloudstaff.myapplication.databinding.ActivityMainBinding
import com.cloudstaff.myapplication.utils.dpToPx
import com.cloudstaff.myapplication.utils.http.Http
import com.cloudstaff.myapplication.utils.http.hasInternetConnection
import com.cloudstaff.myapplication.utils.prefs.PrefsHelper
import com.cloudstaff.myapplication.utils.retrofit.ApiService
import com.cloudstaff.myapplication.utils.retrofit.Inputs
import com.cloudstaff.myapplication.utils.retrofit.Locations
import com.cloudstaff.myapplication.utils.retrofit.Payload
import com.cloudstaff.myapplication.utils.retrofit.PointsOfInterest
import com.cloudstaff.myapplication.utils.retrofit.api
import com.cloudstaff.myapplication.utils.retrofit.token
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

	private lateinit var gmap: GoogleMap
	private lateinit var fusedLocationClient: FusedLocationProviderClient
	private lateinit var prefs: PrefsHelper
	private var currentPolyline: Polyline? = null
	private var currentLatLng: LatLng? = null
	private var showOccupants: Boolean = true

	private val requestNotificationPermission =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
			if (isGranted) {
				// Permission granted
				Log.d("MainActivity", "Notification permission granted")
				requestLocationPermission()
			} else {
				// Permission denied
				Log.d("MainActivity", "Notification permission denied")
				requestLocationPermission()
			}
		}

	private lateinit var binding: ActivityMainBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
		FirebaseMessaging.getInstance().token
			.addOnCompleteListener { task ->
				if (!task.isSuccessful) {
					// Failed to get token
					Log.w("FCM", "Fetching FCM token failed", task.exception)
					return@addOnCompleteListener
				}

				// Get new FCM token
				val token = task.result
				Log.d("FCM", "FCM Token: $token")
			}

		fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

		val mapFragment = supportFragmentManager
			.findFragmentById(R.id.map) as SupportMapFragment
		mapFragment.getMapAsync(this)

		prefs = PrefsHelper(this)

		setupClickListeners()

		// Check and request permission on start
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (ContextCompat.checkSelfPermission(
					this,
					Manifest.permission.POST_NOTIFICATIONS
				) != PackageManager.PERMISSION_GRANTED
			) {
				requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
			}
		}
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
			showOccupants = true
			fetchEvacCenters()
		}

		binding.reliefGoodsOps.setOnClickListener {
			showOccupants = false
			fetchReliefGoodsOps()
		}

		binding.hospitals.setOnClickListener {
			showOccupants = true
			fetchHospitals()
		}

		binding.fab.setOnClickListener {
			val intent = Intent(this, CameraActivity::class.java)
			startActivity(intent)
		}
	}

	override fun onMapReady(googleMap: GoogleMap) {
		gmap = googleMap
		gmap.apply {
			uiSettings.isCompassEnabled = true
			uiSettings.isZoomControlsEnabled = true
			uiSettings.isMyLocationButtonEnabled = true
			uiSettings.isRotateGesturesEnabled = true
			uiSettings.isTiltGesturesEnabled = true
			setPadding(0, 80.dpToPx(), 0, 250)

			if (ContextCompat.checkSelfPermission(
					this@MainActivity,
					Manifest.permission.ACCESS_FINE_LOCATION
				) == PackageManager.PERMISSION_GRANTED
			) {
				isMyLocationEnabled = true
			}
		}

		requestLocationPermission()

		gmap.setOnMarkerClickListener { marker ->
			val loc = marker.tag as? Locations ?: return@setOnMarkerClickListener false
			val destination = LatLng(loc.coordinates.lat, loc.coordinates.lng)

			// Draw route
			drawRouteTo(destination)

			// Show info window
			marker.showInfoWindow()
			showLocationsBottomSheet(loc)

			// Move camera so info window is fully visible, after layout
			binding.map.post {
				val offsetPixels = 150 // adjust based on info window height
				val projection = gmap.projection
				val markerPoint = projection.toScreenLocation(marker.position)
				val targetPoint = Point(markerPoint.x, markerPoint.y - offsetPixels)
				val targetLatLng = projection.fromScreenLocation(targetPoint)

				val currentZoom = gmap.cameraPosition.zoom
				val currentTilt = gmap.cameraPosition.tilt
				val cameraPosition = CameraPosition.Builder()
					.target(targetLatLng)
					.zoom(currentZoom)
					.tilt(currentTilt)
					.bearing(1f) // small bearing to show compass
					.build()

				gmap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
			}

			true // consume click
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
			toast("Fetching your location...", Toast.LENGTH_SHORT)

			fusedLocationClient.lastLocation.addOnSuccessListener { location ->
				if (location != null) {
					currentLatLng = LatLng(location.latitude, location.longitude)
					val cameraPosition = CameraPosition.Builder()
						.target(currentLatLng ?: return@addOnSuccessListener)
						.zoom(16f)
						.bearing(1f)
						.tilt(0f)
						.build()
					gmap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
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

	private fun getAddressFromCurrentLatLng(): Pair<String?, String?> {
		val geocoder = Geocoder(this, Locale.getDefault())

		val latLng = currentLatLng ?: return Pair(null, null)
		val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

		if (addresses.isNullOrEmpty()) return Pair(null, null)

		val address = addresses[0]

		val city = address.locality         // e.g. "Angeles City"
		val barangay = address.subLocality  // e.g. "Mining" or "Pulung Maragul"

		return Pair(city, barangay)
	}

	private fun fetchEvacCenters() {
		if (hasInternetConnection(this)) {
			lifecycleScope.launch {
				binding.prb.visibility = View.VISIBLE
				try {
					val data = fetchPointsOfInterest(
						currentLatLng = currentLatLng,
						type = "evacuations"
					)
					val evacCenters = data.nearest_evacuations ?: return@launch

					prefs.clearEvacuationCenters()
					prefs.addEvacuationCenters(evacCenters)

					addMarkers(evacCenters)

					binding.prb.visibility = View.INVISIBLE
				} catch (e: Exception) {
					binding.prb.visibility = View.INVISIBLE
					toast("An error has occurred: ${e.message}")
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
					val data = fetchPointsOfInterest(
						currentLatLng = currentLatLng,
						type = "relief goods"
					)
					val reliefGoods = data.nearest_relief_goods ?: return@launch

					prefs.clearReliefGoodsOps()
					prefs.addReliefGoodsOps(reliefGoods)

					addMarkers(reliefGoods)

					binding.prb.visibility = View.INVISIBLE
				} catch (e: Exception) {
					binding.prb.visibility = View.INVISIBLE
					toast("An error has occurred: ${e.message}")
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
					val data = fetchPointsOfInterest(
						currentLatLng = currentLatLng,
						type = "hospitals"
					)
					val hospitals = data.nearest_hospitals ?: return@launch

					prefs.clearHospitals()
					prefs.addHospitals(hospitals)

					addMarkers(hospitals)

					binding.prb.visibility = View.INVISIBLE
				} catch (e: Exception) {
					binding.prb.visibility = View.INVISIBLE
					toast("An error has occurred: ${e.message}")
				}
			}
		} else {
			val hospitals = prefs.getHospitals()
			addMarkers(hospitals)
		}
	}

	suspend fun fetchPointsOfInterest(
		currentLatLng: LatLng?,
		type: String
	): PointsOfInterest {

		if (currentLatLng == null) {
			throw Exception("Current location unavailable")
		}

		// 1. Get city + barangay
		val (city, barangay) = getAddressFromCurrentLatLng()

		// 2. Build payload
		val payload = Payload(
			inputs = Inputs(
				type = type, // e.g. "hospitals" or "evacuation"
				barangay = barangay ?: "",
				city = city ?: "",
				coordinate = "${currentLatLng.latitude},${currentLatLng.longitude}"
			)
		)

		// 3. Execute API call
		val response = api.postData("Bearer $token", payload)

		// 4. Extract the 'area' field (which is a JSON string)
		val rawArea = response.data.outputs.area

		// 5. Deserialize into PointsOfInterest
		return Http.json.decodeFromString(rawArea)
	}

	private fun addMarkers(locations: List<Locations>?) {
		if (locations.isNullOrEmpty()) {
			toast("No locations found")
			return
		} else {
			gmap.clear()
		}

		val boundsBuilder = LatLngBounds.Builder()

		locations.forEach { loc ->
			val position = LatLng(loc.coordinates.lat, loc.coordinates.lng)

			// Create a custom marker with text and pin
			val markerIcon = createMarkerWithTextAndPin(
				text = loc.name ?: loc.building ?: "-",
				pinResId = R.drawable.google_maps_pin,
				pinWidth = 100,
				pinHeight = 100
			)

			val marker = gmap.addMarker(
				MarkerOptions()
					.position(position)
					.icon(markerIcon)
					.anchor(0.5f, 1f)
			)

			marker?.tag = loc
			boundsBuilder.include(position)
		}

		gmap.setOnMapLoadedCallback {
			val bounds = boundsBuilder.build()
			val padding = 120
			gmap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
		}
	}

	private fun createMarkerWithTextAndPin(text: String, pinResId: Int, pinWidth: Int = 80, pinHeight: Int = 80): BitmapDescriptor {
		// Paint for text
		val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.BLACK
			textSize = 35f
			typeface = Typeface.DEFAULT_BOLD
			textAlign = Paint.Align.CENTER
		}

		val textBounds = Rect()
		textPaint.getTextBounds(text, 0, text.length, textBounds)

		val padding = 20

		// Resize pin icon
		val originalPin = BitmapFactory.decodeResource(resources, pinResId)
		val pinBitmap = Bitmap.createScaledBitmap(originalPin, pinWidth, pinHeight, true)

		// Calculate total bitmap size
		val width = maxOf(pinBitmap.width, textBounds.width() + padding * 2)
		val height = pinBitmap.height + textBounds.height() + padding * 3

		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)

		// Draw white rounded rectangle behind text
		val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.WHITE
			style = Paint.Style.FILL
		}
		val rect = RectF(
			(width - textBounds.width() - padding).toFloat() / 2,
			0f,
			(width + textBounds.width() + padding).toFloat() / 2,
			textBounds.height() + padding.toFloat()
		)
		canvas.drawRoundRect(rect, 10f, 10f, rectPaint)

		// Draw the text
		canvas.drawText(text, width / 2f, textBounds.height().toFloat(), textPaint)

		// Draw the resized pin below the text
		canvas.drawBitmap(pinBitmap, (width - pinBitmap.width) / 2f, textBounds.height() + padding.toFloat(), null)

		return BitmapDescriptorFactory.fromBitmap(bitmap)
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
			if (!hasInternetConnection(this@MainActivity)) return@launch

			val origin = currentLatLng ?: return@launch

			val points = getRouteNewApi(origin, destination, BuildConfig.MAPS_API_KEY)
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

	private fun showLocationsBottomSheet(loc: Locations) {
		val dialog = BottomSheetDialog(this)
		val view = layoutInflater.inflate(R.layout.bottom_sheet, null)

		dialog.window?.setDimAmount(0f)
		dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

		view.findViewById<TextView>(R.id.txtBuilding).text = loc.name ?: loc.building
		view.findViewById<TextView>(R.id.txtAddress).text = loc.address
		view.findViewById<TextView>(R.id.txtDistance).text = "${loc.distance_km} km away"
		if (showOccupants) {
			view.findViewById<TextView>(R.id.txtOccupancy).visibility = View.VISIBLE
			view.findViewById<TextView>(R.id.txtOccupancy).text = "Current occupants: ${loc.address.length}"
		} else {
			view.findViewById<TextView>(R.id.txtOccupancy).visibility = View.GONE
		}

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

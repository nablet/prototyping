package com.cloudstaff.myapplication.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

	private lateinit var gmap: GoogleMap
	private lateinit var fusedLocationClient: FusedLocationProviderClient
	private lateinit var prefs: PrefsHelper


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
			// Retrieve the attached data
			val center = marker.tag as? EvacuationCenter
			if (center != null) {
				// Now you have access to the full data class
				Toast.makeText(
					this,
					"Clicked: ${center.building}, Address: ${center.address}, Distance: ${center.distance_km} km",
					Toast.LENGTH_LONG
				).show()

				// Example: move camera to the marker
				gmap.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
			}

			true // return true to consume the click
		}

		gmap.setOnInfoWindowClickListener { marker ->
			val center = marker.tag as? EvacuationCenter
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
					val currentLatLng = LatLng(location.latitude, location.longitude)
					gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
				}
			}
		}
	}

	private fun fetchEvacCenters() {

		if (hasInternetConnection(this)) {
			lifecycleScope.launch {
				val body = mapOf(
					"inputs" to mapOf(
						"barangay" to "mining",
						"city" to "Pampanga",
						"coordinate" to "14.5995, 120.9842"
					).toString(),
					"user" to "erwinf-user-1"
				)
				val response = Http.postJson<DifyResponse>("https://dify-hackaton.cloudstaff.io/v1/workflows/run", body)
				println(response)
			}

		} else {
			 val evacCenters = prefs.get<List<EvacuationCenter>>("evac")
			evacCenters?.forEach { evacCenter ->
				val marker = gmap.addMarker(
					MarkerOptions()
						.position(LatLng(evacCenter.coordinates.latitude, evacCenter.coordinates.longitude))
						.title(evacCenter.building)
						.snippet(evacCenter.address)
				)
				marker?.tag = evacCenter
				marker?.showInfoWindow()
			}
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

data class EvacuationCenter(
	val building: String,
	val address: String,
	val coordinates: Coordinates,
	val distance_km: Double,
)

data class Coordinates(
	val latitude: Double,
	val longitude: Double,
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

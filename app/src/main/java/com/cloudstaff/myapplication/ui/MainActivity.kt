package com.cloudstaff.myapplication.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cloudstaff.myapplication.databinding.ActivityMainBinding
import com.cloudstaff.myapplication.utils.http.Http
import com.cloudstaff.myapplication.utils.listview.ListViewHelper
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : AppCompatActivity() {

	private lateinit var binding: ActivityMainBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// set token here
		Http.bearerToken = null



		// Sample code below
		lifecycleScope.launch {
			@Serializable data class SampleItems(val id: String, val name: String)

			val response: List<SampleItems> = Http.getJson("https://api.restful-api.dev/objects")

			ListViewHelper.setup(
				listView = binding.lvSample,
				items = response
			)
		}
	}

}

package com.cloudstaff.myapplication.utils.prefs

import android.content.Context
import android.content.SharedPreferences
import com.cloudstaff.myapplication.ui.Locations
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefsHelper(context: Context, prefName: String = "app_prefs") {

	val prefs: SharedPreferences =
		context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
	val gson = Gson()

	/** Save any data class */
	fun <T> put(key: String, data: T) {
		val json = gson.toJson(data)
		prefs.edit().putString(key, json).apply()
	}

	/** Retrieve any data class */
	inline fun <reified T> get(key: String, defaultValue: T? = null): T? {
		val json = prefs.getString(key, null) ?: return defaultValue
		return try {
			gson.fromJson(json, object : TypeToken<T>() {}.type)
		} catch (e: Exception) {
			e.printStackTrace()
			defaultValue
		}
	}

	/** Remove a key */
	fun remove(key: String) {
		prefs.edit().remove(key).apply()
	}

	/** Clear all data */
	fun clear() {
		prefs.edit().clear().apply()
	}



	/** Add multiple evacuation centers */
	fun addEvacuationCenters(centers: List<Locations>) {
		val list: MutableList<Locations> = getEvacuationCenters()?.toMutableList() ?: mutableListOf()
		list.addAll(centers)
		put("evacuation_centers", list)
	}

	/** Get all evacuation centers */
	fun getEvacuationCenters(): List<Locations>? {
		return get("evacuation_centers")
	}

	/** Relief goods */
	fun addReliefGoodsOps(centers: List<Locations>) {
		val list: MutableList<Locations> = getEvacuationCenters()?.toMutableList() ?: mutableListOf()
		list.addAll(centers)
		put("evacuation_centers", list)
	}

	fun getReliefGoodsOps(): List<Locations>? {
		return get("relief_goods_ops")
	}

	/** Hospitals */
	fun addHospitals(centers: List<Locations>) {
		val list: MutableList<Locations> = getEvacuationCenters()?.toMutableList() ?: mutableListOf()
		list.addAll(centers)
		put("hospitals", list)
	}

	fun getHospitals(): List<Locations>? {
		return get("hospitals")
	}


	/** Remove all evacuation centers */
	fun clearEvacuationCenters() {
		prefs.edit().remove("evacuation_centers").apply()
	}
}
